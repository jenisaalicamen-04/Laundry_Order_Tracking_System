package com.scc.dao;

import com.scc.model.LaundryOrder;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for laundry orders. Customers can only reach their own orders.
 * Orders are never deleted: cancelling changes the status to Cancelled.
 */
public class OrderDao {
    private static final String SELECT_ORDER =
            "SELECT o.order_id, o.customer_id, u.full_name, o.service_type, o.order_date, o.total_amount, "
                    + "o.order_status, o.last_updated, o.notes "
                    + "FROM laundry_orders o JOIN users u ON o.customer_id = u.user_id";

    private final ActivityLogDao logDao = new ActivityLogDao();

    // =====================================================================
    // CUSTOMER
    // =====================================================================

    // The customer ID always comes from the logged-in account. Returns the new order ID.
    public int createOrder(User customer, String serviceType, String notes) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        String now = DatabaseConnection.now();
        String sql = "INSERT INTO laundry_orders (customer_id, service_type, order_date, total_amount, "
                + "order_status, last_updated, notes) VALUES (?, ?, ?, NULL, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, customer.userId());
                    ps.setString(2, serviceType);
                    ps.setString(3, now);
                    ps.setString(4, LaundryOrder.STATUS_PENDING);
                    ps.setString(5, now);
                    if (notes == null || notes.isEmpty()) {
                        ps.setNull(6, Types.VARCHAR);
                    } else {
                        ps.setString(6, notes);
                    }
                    ps.executeUpdate();
                }
                int orderId = DatabaseConnection.lastInsertId(conn);
                logDao.addLog(conn, customer.userId(), "New laundry order created: " + serviceType, orderId);
                conn.commit(); // the order and its log are saved together
                return orderId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Current orders: Pending, Processing, and Ready.
    public List<LaundryOrder> findCurrentOrders(User customer) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        return query(" WHERE o.customer_id = ? AND o.order_status IN ('Pending', 'Processing', 'Ready') "
                + "ORDER BY o.order_id DESC", customer.userId());
    }

    // Previous orders: Completed and Cancelled.
    public List<LaundryOrder> findOrderHistory(User customer) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        return query(" WHERE o.customer_id = ? AND o.order_status IN ('Completed', 'Cancelled') "
                + "ORDER BY o.order_id DESC", customer.userId());
    }

    public List<LaundryOrder> findCustomerOrdersByStatus(User customer, String status) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        return query(" WHERE o.customer_id = ? AND o.order_status = ? ORDER BY o.order_id DESC",
                customer.userId(), status);
    }

    // Finds one order, but only if it belongs to this customer (other customers' orders are never found).
    public LaundryOrder findCustomerOrder(User customer, int orderId) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        List<LaundryOrder> found = query(" WHERE o.order_id = ? AND o.customer_id = ?", orderId, customer.userId());
        return found.isEmpty() ? null : found.get(0);
    }

    // Cancels the customer's own order, only while it is Pending. Returns true if it was cancelled.
    public boolean cancelOwnOrder(User customer, int orderId) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        String sql = "UPDATE laundry_orders SET order_status = 'Cancelled', last_updated = ? "
                + "WHERE order_id = ? AND customer_id = ? AND order_status = 'Pending'";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, DatabaseConnection.now());
                    ps.setInt(2, orderId);
                    ps.setInt(3, customer.userId());
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, customer.userId(), "Order cancelled by customer", orderId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // =====================================================================
    // STAFF AND ADMIN (reading)
    // =====================================================================

    public List<LaundryOrder> findAll(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        return query(" ORDER BY o.order_id DESC");
    }

    public LaundryOrder findById(User actor, int orderId) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        List<LaundryOrder> found = query(" WHERE o.order_id = ?", orderId);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<LaundryOrder> searchByCustomerName(User actor, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        return query(" WHERE u.full_name LIKE ? ORDER BY o.order_id DESC", "%" + keyword + "%");
    }

    public List<LaundryOrder> findByStatus(User actor, String status) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        return query(" WHERE o.order_status = ? ORDER BY o.order_id DESC", status);
    }

    // =====================================================================
    // STAFF (updating)
    // =====================================================================

    // Moves an order to a new status. Returns false if the order changed in the meantime.
    // "AND order_status = ?" makes sure the order still has the status that was shown on screen.
    public boolean changeStatus(User staff, LaundryOrder order, String newStatus, String logText) throws SQLException {
        Permissions.require(staff, User.ROLE_STAFF);
        if (!LaundryOrder.isValidTransition(order.orderStatus(), newStatus)) {
            return false;
        }
        String sql = "UPDATE laundry_orders SET order_status = ?, last_updated = ? "
                + "WHERE order_id = ? AND order_status = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, newStatus);
                    ps.setString(2, DatabaseConnection.now());
                    ps.setInt(3, order.orderId());
                    ps.setString(4, order.orderStatus());
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, staff.userId(), logText, order.orderId());
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean updateServiceType(User staff, LaundryOrder order, String newService) throws SQLException {
        return updateOrderField(staff, order, "service_type", newService,
                "Order service type updated: " + order.serviceType() + " -> " + newService);
    }

    public boolean updateTotalAmount(User staff, LaundryOrder order, double amount) throws SQLException {
        return updateOrderField(staff, order, "total_amount", amount,
                "Order total amount updated: " + order.amountText() + " -> " + String.format("%.2f", amount));
    }

    // Saves one changed column of an order (and its log) in one transaction.
    // The column name is always written in the code above, never typed by a user.
    private boolean updateOrderField(User staff, LaundryOrder order, String column, Object value, String logText)
            throws SQLException {
        Permissions.require(staff, User.ROLE_STAFF);
        String sql = "UPDATE laundry_orders SET " + column + " = ?, last_updated = ? "
                + "WHERE order_id = ? AND order_status NOT IN ('Completed', 'Cancelled')";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (value instanceof Double) {
                        ps.setDouble(1, (Double) value);
                    } else {
                        ps.setString(1, value.toString());
                    }
                    ps.setString(2, DatabaseConnection.now());
                    ps.setInt(3, order.orderId());
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, staff.userId(), logText, order.orderId());
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // =====================================================================
    // Helper
    // =====================================================================

    // Runs the SELECT. "suffix" is written in this file; user values are only passed as parameters.
    private List<LaundryOrder> query(String suffix, Object... params) throws SQLException {
        List<LaundryOrder> orders = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ORDER + suffix)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Integer) {
                    ps.setInt(i + 1, (Integer) params[i]);
                } else {
                    ps.setString(i + 1, params[i].toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double amount = rs.getDouble("total_amount");
                    Double total = rs.wasNull() ? null : amount;
                    orders.add(new LaundryOrder(rs.getInt("order_id"), rs.getInt("customer_id"),
                            rs.getString("full_name"), rs.getString("service_type"), rs.getString("order_date"),
                            total, rs.getString("order_status"), rs.getString("last_updated"), rs.getString("notes")));
                }
            }
        }
        return orders;
    }
}