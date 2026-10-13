package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/**
 * Admin screens for accounts: create, view, search, edit, approve pending accounts, and activate / deactivate.
 * Accounts are never deleted. The same screens are used for Customers, Staff, and (reduced) Admins.
 */
public class AccountMenu {
    private final InputHelper input;
    private final UserDao userDao = new UserDao();
    private final AccountForm form;

    public AccountMenu(InputHelper input) {
        this.input = input;
        this.form = new AccountForm(input, userDao);
    }

    // role is CUSTOMER, STAFF, or ADMIN
    public void run(User actor, String role) {
        boolean back = false;
        while (!back) {
            boolean canCreateAndEdit = !role.equals(User.ROLE_ADMIN); // admins register themselves
            InputHelper.header("MANAGE " + label(role).toUpperCase() + " ACCOUNTS");
            if (canCreateAndEdit) {
                System.out.println("1. Create Account");
                System.out.println("2. View Accounts");
                System.out.println("3. Search Accounts");
                System.out.println("4. Edit Account");
                System.out.println("5. Approve Pending Accounts");
                System.out.println("6. Activate / Deactivate Account");
                System.out.println("7. Back");
            } else {
                System.out.println("1. View Accounts");
                System.out.println("2. Approve Pending Accounts");
                System.out.println("3. Activate / Deactivate Account");
                System.out.println("4. Back");
            }
            int choice = input.readMenuChoice("Enter your choice: ", 1, canCreateAndEdit ? 7 : 4);
            // Admin screens use the same code numbers as the full menu.
            int action = canCreateAndEdit ? choice : (choice == 1 ? 2 : choice == 2 ? 5 : choice == 3 ? 6 : 7);

            try {
                switch (action) {
                    case 1:
                        createAccount(actor, role);
                        break;
                    case 2:
                        showAccounts(actor, role, null, null);
                        break;
                    case 3:
                        String keyword = input.readNonEmpty("Search name or username: ");
                        showAccounts(actor, role, null, keyword);
                        break;
                    case 4:
                        editAccount(actor, role);
                        break;
                    case 5:
                        approvePending(actor, role);
                        break;
                    case 6:
                        changeStatus(actor, role);
                        break;
                    default:
                        back = true;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    private String label(String role) {
        return role.equals(User.ROLE_STAFF) ? "Staff" : (role.equals(User.ROLE_ADMIN) ? "Admin" : "Customer");
    }

    // Admin creates a Customer or Staff account. It is ACTIVE immediately.
    private void createAccount(User actor, String role) throws SQLException {
        InputHelper.header("CREATE " + label(role).toUpperCase() + " ACCOUNT");
        String fullName = input.readNonEmpty("Full Name: ");
        String contact = form.readContact("Contact Number: ");
        String address = input.readNonEmpty("Address: ");
        String username = form.readNewUsername();
        String password = form.readNewPassword();
        int newId = userDao.createAccount(actor, fullName, contact, address, username, password, role);
        System.out.println("Account created successfully (status ACTIVE). User ID: " + newId);
    }

    private void showAccounts(User actor, String role, String status, String keyword) throws SQLException {
        List<User> users = userDao.findByRole(actor, role, status, keyword);
        System.out.println();
        if (users.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        System.out.println(String.format("%-5s %-14s %-22s %-16s %-9s %s",
                "ID", "Username", "Full Name", "Contact", "Status", "Created"));
        InputHelper.printDivider();
        for (User u : users) { // password hashes are never shown
            System.out.println(String.format("%-5d %-14s %-22s %-16s %-9s %s",
                    u.userId(), u.username(), InputHelper.cut(u.fullName(), 22), u.contactNumber(),
                    u.accountStatus(), u.createdAt()));
            System.out.println("      Address: " + u.address());
        }
        InputHelper.printDivider();
        System.out.println("Accounts shown: " + users.size());
    }

    private void editAccount(User actor, String role) throws SQLException {
        InputHelper.header("EDIT ACCOUNT");
        int userId = input.readInt("Enter User ID: ");
        User target = userDao.findByIdAndRole(userId, role);
        if (target == null) {
            System.out.println("Account not found.");
            return;
        }
        boolean done = false;
        while (!done) {
            target = userDao.findByIdAndRole(userId, role); // reload to show the saved values
            System.out.println();
            System.out.println("Editing: " + target.username() + " (" + target.roleLabel() + ")");
            System.out.println("Full Name: " + target.fullName());
            System.out.println("Contact: " + target.contactNumber());
            System.out.println("Address: " + target.address());
            System.out.println("1. Change Full Name");
            System.out.println("2. Change Contact Number");
            System.out.println("3. Change Address");
            System.out.println("4. Reset Password");
            System.out.println("5. Done");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);
            switch (choice) {
                case 1:
                    userDao.updateField(actor, target, "full_name", input.readNonEmpty("New Full Name: "));
                    System.out.println("Saved: full name updated.");
                    break;
                case 2:
                    userDao.updateField(actor, target, "contact_number", form.readContact("New Contact Number: "));
                    System.out.println("Saved: contact number updated.");
                    break;
                case 3:
                    userDao.updateField(actor, target, "address", input.readNonEmpty("New Address: "));
                    System.out.println("Saved: address updated.");
                    break;
                case 4:
                    userDao.resetPassword(actor, target, form.readNewPassword());
                    System.out.println("Saved: password updated.");
                    break;
                default:
                    done = true;
                    break;
            }
        }
    }

    // Accounts that registered themselves are PENDING. The admin approves or rejects them here.
    private void approvePending(User actor, String role) throws SQLException {
        InputHelper.header("PENDING " + label(role).toUpperCase() + " ACCOUNTS");
        List<User> pending = userDao.findByRole(actor, role, User.STATUS_PENDING, null);
        if (pending.isEmpty()) {
            System.out.println("There are no pending accounts.");
            return;
        }
        showAccounts(actor, role, User.STATUS_PENDING, null);
        int userId = input.readInt("Enter the User ID to review (0 to go back): ");
        if (userId == 0) {
            return;
        }
        User target = userDao.findByIdAndRole(userId, role);
        if (target == null || !target.isPending()) {
            System.out.println("No pending account found with that ID.");
            return;
        }
        System.out.println("Account: " + target.username() + " (" + target.fullName() + ")");
        System.out.println("1. Approve (status becomes ACTIVE)");
        System.out.println("2. Reject (status becomes INACTIVE)");
        System.out.println("3. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
        if (choice == 1) {
            userDao.setStatus(actor, target, User.STATUS_ACTIVE, "User account approved: " + target.username());
            System.out.println("Account approved. The user can now log in.");
        } else if (choice == 2) {
            userDao.setStatus(actor, target, User.STATUS_INACTIVE, "User account rejected: " + target.username());
            System.out.println("Account rejected. The record is kept.");
        }
    }

    // ACTIVE <-> INACTIVE. Pending accounts must be approved or rejected instead.
    private void changeStatus(User actor, String role) throws SQLException {
        InputHelper.header("ACTIVATE / DEACTIVATE ACCOUNT");
        int userId = input.readInt("Enter User ID: ");
        User target = userDao.findByIdAndRole(userId, role);
        if (target == null) {
            System.out.println("Account not found.");
            return;
        }
        System.out.println("Account: " + target.username() + " (" + target.fullName() + ")");
        System.out.println("Current status: " + target.accountStatus());
        if (target.isPending()) {
            System.out.println("This account is PENDING. Use \"Approve Pending Accounts\" instead.");
            return;
        }
        boolean deactivate = target.isActive();
        String question = deactivate ? "Deactivate this account? (Y/N): " : "Activate this account? (Y/N): ";
        if (!input.readConfirm(question)) {
            System.out.println("No changes were made.");
            return;
        }
        String newStatus = deactivate ? User.STATUS_INACTIVE : User.STATUS_ACTIVE;
        String logText = (deactivate ? "User account deactivated: " : "User account activated: ") + target.username();
        if (userDao.setStatus(actor, target, newStatus, logText)) {
            System.out.println(deactivate
                    ? "Account deactivated. The record is kept, but the user can no longer log in."
                    : "Account activated.");
        } else {
            System.out.println("Cannot deactivate this account. The system needs at least one active admin.");
        }
    }
}