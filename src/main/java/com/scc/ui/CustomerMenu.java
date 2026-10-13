package com.scc.ui;

import com.scc.dao.OrderDao;
import com.scc.dao.PasswordHasher;
import com.scc.dao.UserDao;
import com.scc.model.LaundryOrder;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** The Customer dashboard: customers only see and change their own orders and profile. */
public class CustomerMenu {
    private final InputHelper input;
    private final OrderDao orderDao = new OrderDao();
    private final UserDao userDao = new UserDao();
    private final AccountForm form;

    public CustomerMenu(InputHelper input) {
        this.input = input;
        this.form = new AccountForm(input, userDao);
    }

    public void run(User customer) {
        User user = customer;
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("CUSTOMER MENU");
            System.out.println("Welcome, " + user.fullName() + "!");
            System.out.println();
            System.out.println("1. Create Laundry Order");
            System.out.println("2. View My Orders");
            System.out.println("3. Check Order Status");
            System.out.println("4. View Order History");
            System.out.println("5. Cancel Pending Order");
            System.out.println("6. View My Profile");
            System.out.println("7. Update My Account");
            System.out.println("8. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 8);

            try {
                switch (choice) {
                    case 1:
                        createOrder(user);
                        break;
                    case 2:
                        InputHelper.header("MY CURRENT ORDERS");
                        OrderView.printTable(orderDao.findCurrentOrders(user), false);
                        break;
                    case 3:
                        checkOrderStatus(user);
                        break;
                    case 4:
                        InputHelper.header("MY ORDER HISTORY");
                        OrderView.printTable(orderDao.findOrderHistory(user), false);
                        break;
                    case 5:
                        cancelOrder(user);
                        break;
                    case 6:
                        viewProfile(user);
                        break;
                    case 7:
                        user = updateAccount(user);
                        break;
                    default:
                        loggedIn = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    private void createOrder(User user) throws SQLException {
        InputHelper.header("CREATE LAUNDRY ORDER");
        String service = input.chooseFromList("Available laundry services:", LaundryOrder.SERVICE_TYPES);
        String notes = input.readOptional("Special instructions (optional, press Enter to skip): ");
        while (notes.length() > 200) {
            System.out.println("Please keep the instructions within 200 characters.");
            notes = input.readOptional("Special instructions (optional, press Enter to skip): ");
        }
        int orderId = orderDao.createOrder(user, service, notes);
        System.out.println();
        System.out.println("Laundry order created successfully!");
        System.out.println("Order ID: " + orderId);
        System.out.println("Service: " + service);
        System.out.println("Status: " + LaundryOrder.STATUS_PENDING);
        System.out.println("The staff will update the total amount after checking your laundry.");
    }

    // The customer searches only their own orders, by order ID or by status.
    private void checkOrderStatus(User user) throws SQLException {
        InputHelper.header("CHECK ORDER STATUS");
        System.out.println("1. Search by Order ID");
        System.out.println("2. Search by Status");
        System.out.println("3. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
        if (choice == 1) {
            int orderId = input.readInt("Enter Order ID: ");
            LaundryOrder order = orderDao.findCustomerOrder(user, orderId);
            if (order == null) {
                System.out.println("Order not found.");
            } else {
                OrderView.printDetails(order);
            }
        } else if (choice == 2) {
            String status = input.chooseFromList("Choose a status:", LaundryOrder.ALL_STATUSES);
            List<LaundryOrder> orders = orderDao.findCustomerOrdersByStatus(user, status);
            OrderView.printTable(orders, false);
        }
    }

    // Only the customer's own order, and only while it is Pending.
    private void cancelOrder(User user) throws SQLException {
        InputHelper.header("CANCEL PENDING ORDER");
        int orderId = input.readInt("Enter Order ID to cancel: ");
        LaundryOrder order = orderDao.findCustomerOrder(user, orderId);
        if (order == null) {
            System.out.println("Order not found.");
            return;
        }
        OrderView.printDetails(order);
        if (!order.orderStatus().equals(LaundryOrder.STATUS_PENDING)) {
            System.out.println("This order cannot be cancelled because its status is " + order.orderStatus()
                    + ". Only Pending orders can be cancelled.");
            return;
        }
        if (!input.readConfirm("Cancel this order? (Y/N): ")) {
            System.out.println("The order was not cancelled.");
            return;
        }
        if (orderDao.cancelOwnOrder(user, orderId)) {
            System.out.println("Order " + orderId + " is now Cancelled.");
        } else {
            System.out.println("The order could not be cancelled. Its status may have changed.");
        }
    }

    private void viewProfile(User user) {
        InputHelper.header("MY PROFILE");
        System.out.println("User ID: " + user.userId());
        System.out.println("Full Name: " + user.fullName());
        System.out.println("Username: " + user.username());
        System.out.println("Contact Number: " + user.contactNumber());
        System.out.println("Address: " + user.address());
        System.out.println("Account Type: " + user.roleLabel());
        System.out.println("Account Status: " + user.accountStatus());
        System.out.println("Member Since: " + user.createdAt());
    }

    // A customer can change only contact number, address, and password.
    // Returns the updated user (records cannot be changed, so a new one is created).
    private User updateAccount(User start) throws SQLException {
        User user = start;
        boolean back = false;
        while (!back) {
            InputHelper.header("UPDATE MY ACCOUNT");
            System.out.println("Contact Number: " + user.contactNumber());
            System.out.println("Address: " + user.address());
            System.out.println();
            System.out.println("1. Change Contact Number");
            System.out.println("2. Change Address");
            System.out.println("3. Change Password");
            System.out.println("4. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
            switch (choice) {
                case 1:
                    String contact = form.readContact("New Contact Number: ");
                    userDao.updateOwnField(user, "contact_number", contact);
                    user = user.withContactNumber(contact);
                    System.out.println("Saved: contact number updated.");
                    break;
                case 2:
                    String address = input.readNonEmpty("New Address: ");
                    userDao.updateOwnField(user, "address", address);
                    user = user.withAddress(address);
                    System.out.println("Saved: address updated.");
                    break;
                case 3:
                    String current = input.readNonEmpty("Current Password: ");
                    if (!PasswordHasher.verify(current, user.passwordHash())) {
                        System.out.println("Wrong current password. Password not changed.");
                    } else {
                        String newHash = PasswordHasher.hash(form.readNewPassword());
                        userDao.updateOwnField(user, "password_hash", newHash);
                        user = user.withPasswordHash(newHash);
                        System.out.println("Saved: password updated.");
                    }
                    break;
                default:
                    back = true;
                    break;
            }
        }
        return user;
    }
}