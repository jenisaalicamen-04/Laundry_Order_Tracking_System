package com.scc.dao;

import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the users table: register, login check, and account management.
 * Accounts are never deleted. Their status is changed instead (PENDING, ACTIVE, INACTIVE).
 */
public class UserDao {
    private static final String SELECT_USER = "SELECT user_id, full_name, contact_number, address, username, "
            + "password_hash, role, account_status, created_at FROM users";

    private final ActivityLogDao logDao = new ActivityLogDao();

    // ---------- Finding users ----------

    public User findByUsername(String username) throws SQLException {
        return findOne(" WHERE username = ?", username);
    }

    public User findById(int userId) throws SQLException {
        return findOne(" WHERE user_id = ?", userId);
    }

    // Finds a user by ID, but only if that user has the given role.
    public User findByIdAndRole(int userId, String role) throws SQLException {
        return findOne(" WHERE user_id = ? AND role = ?", userId, role);
    }

    public boolean usernameExists(String username) throws SQLException {
        return findByUsername(username) != null;
    }

    // Admin: accounts of one role, optionally filtered by status and/or a name/username keyword.
    public List<User> findByRole(User actor, String role, String status, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        StringBuilder sql = new StringBuilder(SELECT_USER + " WHERE role = ?");
        List<Object> params = new ArrayList<>();
        params.add(role);
        if (status != null) {
            sql.append(" AND account_status = ?");
            params.add(status);
        }
        if (keyword != null) {
            sql.append(" AND (full_name LIKE ? OR username LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY user_id");

        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, params.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUser(rs));
                }
            }
        }
        return users;
    }

    private User findOne(String where, Object... params) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_USER + where)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Integer) {
                ps.setInt(i + 1, (Integer) params[i]);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getInt("user_id"), rs.getString("full_name"), rs.getString("contact_number"),
                rs.getString("address"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("role"), rs.getString("account_status"), rs.getString("created_at"));
    }

    // ---------- Login and logout ----------

    // Checks the username and password against the database.
    // Returns the user if the password is correct (any status), or null if the login details are wrong.
    public User authenticate(String username, String password) throws SQLException {
        User user = findByUsername(username);
        if (user == null || !PasswordHasher.verify(password, user.passwordHash())) {
            return null;
        }
        return user;
    }

    public void recordLogin(User user) throws SQLException {
        logDao.addLog(user.userId(), "Login", null);
    }

    public void recordLogout(User user) throws SQLException {
        logDao.addLog(user.userId(), "Logout", null);
    }

    // ---------- Register ----------

    // Saves a new account. The status is PENDING until an admin approves it.
    // Exception: when the system has no admin yet, the first Admin account is ACTIVE so someone can approve the others.
    public User register(String fullName, String contact, String address, String username,
                         String password, String role) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String status = User.STATUS_PENDING;
                String note = "";
                if (role.equals(User.ROLE_ADMIN) && countAdmins(conn) == 0) {
                    status = User.STATUS_ACTIVE;
                    note = " - first admin, activated automatically";
                }
                int newId = insertUser(conn, fullName, contact, address, username, password, role, status);
                logDao.addLog(conn, newId, "Account registered: " + username + " (" + role + ")" + note, null);
                conn.commit();
                return findById(newId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private int countAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Saves a user row and returns the new user ID. Only the password HASH is saved.
    private int insertUser(Connection conn, String fullName, String contact, String address, String username,
                           String password, String role, String status) throws SQLException {
        String sql = "INSERT INTO users (full_name, contact_number, address, username, password_hash, "
                + "role, account_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, contact);
            ps.setString(3, address);
            ps.setString(4, username);
            ps.setString(5, PasswordHasher.hash(password));
            ps.setString(6, role);
            ps.setString(7, status);
            ps.setString(8, DatabaseConnection.now());
            ps.executeUpdate();
        }
        return DatabaseConnection.lastInsertId(conn);
    }

    // ---------- Admin: account management ----------

    // Admin creates a Customer or Staff account. These are ACTIVE immediately.
    public int createAccount(User actor, String fullName, String contact, String address, String username,
                             String password, String role) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int newId = insertUser(conn, fullName, contact, address, username, password, role, User.STATUS_ACTIVE);
                logDao.addLog(conn, actor.userId(), "User account created: " + username + " (" + role + ")", null);
                conn.commit();
                return newId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin edits one field. field must be one of: full_name, contact_number, address.
    public void updateField(User actor, User target, String field, String value) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        if (!field.equals("full_name") && !field.equals("contact_number") && !field.equals("address")) {
            throw new IllegalArgumentException("This field cannot be edited.");
        }
        saveField(actor.userId(), target.userId(), field, value,
                "User account edited: " + target.username() + " (" + field + " updated)");
    }

    // Admin resets the password of a Customer or Staff account.
    public void resetPassword(User actor, User target, String newPassword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        saveField(actor.userId(), target.userId(), "password_hash", PasswordHasher.hash(newPassword),
                "User account edited: " + target.username() + " (password reset)");
    }

    // Customer changes their OWN contact number, address, or password. Role and status cannot be changed here.
    public void updateOwnField(User customer, String field, String value) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        if (!field.equals("contact_number") && !field.equals("address") && !field.equals("password_hash")) {
            throw new IllegalArgumentException("This field cannot be edited.");
        }
        String what = field.equals("password_hash") ? "password" : field.replace('_', ' ');
        saveField(customer.userId(), customer.userId(), field, value,
                "Customer account information updated (" + what + ")");
    }

    // Updates ONE column and writes the log in the same transaction.
    // The column name is always checked above, never taken directly from user input.
    private void saveField(int actorId, int targetId, String column, String value, String logText) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET " + column + " = ? WHERE user_id = ?")) {
                    ps.setString(1, value);
                    ps.setInt(2, targetId);
                    ps.executeUpdate();
                }
                logDao.addLog(conn, actorId, logText, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin approves, rejects, activates, or deactivates an account.
    // Returns false (and changes nothing) when it would leave the system without an active admin.
    public boolean setStatus(User actor, User target, String newStatus, String logText) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (target.isAdmin() && !newStatus.equals(User.STATUS_ACTIVE)
                    && (target.userId() == actor.userId() || countActiveAdmins(conn) <= 1)) {
                return false; // safeguard: keep at least one active admin
            }
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET account_status = ? WHERE user_id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setInt(2, target.userId());
                    ps.executeUpdate();
                }
                logDao.addLog(conn, actor.userId(), logText, null);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin: how many accounts are waiting for approval.
    public int countPendingAccounts(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE account_status = 'PENDING'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countActiveAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND account_status = 'ACTIVE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}