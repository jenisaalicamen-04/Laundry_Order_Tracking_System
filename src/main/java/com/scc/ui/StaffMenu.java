package com.scc.ui;

import com.scc.dao.OrderDao;
import com.scc.model.LaundryOrder;
import com.scc.model.User;
import java.sql.SQLException;

/** The Laundry Staff dashboard: staff work with all orders, but cannot manage accounts or reports. */
public class StaffMenu {
    private final InputHelper input;
    private final OrderDao orderDao = new OrderDao();
    private final OrderBrowser browser;

    public StaffMenu(InputHelper input) {
        this.input = input;
        this.browser = new OrderBrowser(input, orderDao);
    }

    public void run(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("LAUNDRY STAFF MENU");
            System.out.println("Welcome, " + user.fullName() + "!");
            System.out.println();
            System.out.println("1. View All Laundry Orders");
            System.out.println("2. Search Order");
            System.out.println("3. Filter Orders by Status");
            System.out.println("4. Update Order Information");
            System.out.println("5. Update Order Status");
            System.out.println("6. Mark Order as Completed");
            System.out.println("7. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);

            try {
                switch (choice) {
                    case 1:
                        browser.viewAll(user);
                        break;
                    case 2:
                        browser.search(user);
                        break;
                    case 3:
                        browser.filterByStatus(user);
                        break;
                    case 4:
                        updateOrderInformation(user);
                        break;
                    case 5:
                        updateOrderStatus(user);
                        break;
                    case 6:
                        markCompleted(user);
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

    // Staff changes the service type and/or the total amount.
    private void updateOrderInformation(User staff) throws SQLException {
        InputHelper.header("UPDATE ORDER INFORMATION");
        int orderId = input.readInt("Enter Order ID: ");
        if (orderDao.findById(staff, orderId) == null) {
            System.out.println("Order not found.");
            return;
        }
        boolean done = false;
        while (!done) {
            LaundryOrder order = orderDao.findById(staff, orderId); // reload so the screen shows the saved values
            System.out.println();
            OrderView.printDetails(order);
            if (order.isFinished()) {
                System.out.println("This order is " + order.orderStatus()
                        + " and is kept as history. It can no longer be edited.");
                return;
            }
            System.out.println("1. Change Service Type");
            System.out.println("2. Change Total Amount");
            System.out.println("3. Done");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
            if (choice == 1) {
                String newService = input.chooseFromList("Choose the new service:", LaundryOrder.SERVICE_TYPES);
                if (newService.equals(order.serviceType())) {
                    System.out.println("That is already the service type. No changes were made.");
                } else if (orderDao.updateServiceType(staff, order, newService)) {
                    System.out.println("Order updated successfully.");
                } else {
                    System.out.println("The order could not be updated. It may have been completed or cancelled.");
                }
            } else if (choice == 2) {
                double newAmount = input.readAmount("New total amount: ");
                if (orderDao.updateTotalAmount(staff, order, newAmount)) {
                    System.out.println("Order updated successfully.");
                } else {
                    System.out.println("The order could not be updated. It may have been completed or cancelled.");
                }
            } else {
                done = true;
            }
        }
    }

    // Staff moves an order to the next status (with confirmation).
    private void updateOrderStatus(User staff) throws SQLException {
        InputHelper.header("UPDATE ORDER STATUS");
        int orderId = input.readInt("Enter Order ID: ");
        LaundryOrder order = orderDao.findById(staff, orderId);
        if (order == null) {
            System.out.println("Order not found.");
            return;
        }
        OrderView.printDetails(order);

        String[] allowed = LaundryOrder.allowedNextStatuses(order.orderStatus());
        if (allowed.length == 0) {
            System.out.println("This order is " + order.orderStatus() + ". Its status can no longer be changed.");
            return;
        }
        String newStatus = input.chooseFromList("Change status from " + order.orderStatus() + " to:", allowed);
        if (!input.readConfirm("Change the status of order " + orderId + " from "
                + order.orderStatus() + " to " + newStatus + "? (Y/N): ")) {
            System.out.println("No changes were made.");
            return;
        }

        String logText;
        if (newStatus.equals(LaundryOrder.STATUS_COMPLETED)) {
            logText = "Order completed (status changed from Ready to Completed)";
        } else if (newStatus.equals(LaundryOrder.STATUS_CANCELLED)) {
            logText = "Order cancelled by staff";
        } else {
            logText = "Order status changed from " + order.orderStatus() + " to " + newStatus;
        }
        saveStatus(staff, order, newStatus, logText);
    }

    // Shortcut: Ready -> Completed after the customer picked up the laundry.
    private void markCompleted(User staff) throws SQLException {
        InputHelper.header("MARK ORDER AS COMPLETED");
        int orderId = input.readInt("Enter Order ID: ");
        LaundryOrder order = orderDao.findById(staff, orderId);
        if (order == null) {
            System.out.println("Order not found.");
            return;
        }
        OrderView.printDetails(order);
        if (!order.orderStatus().equals(LaundryOrder.STATUS_READY)) {
            System.out.println("Only orders with status Ready can be marked as Completed. This order is "
                    + order.orderStatus() + ".");
            return;
        }
        if (!input.readConfirm("Has the customer picked up the laundry? Mark as Completed? (Y/N): ")) {
            System.out.println("No changes were made.");
            return;
        }
        saveStatus(staff, order, LaundryOrder.STATUS_COMPLETED,
                "Order completed (status changed from Ready to Completed)");
    }

    private void saveStatus(User staff, LaundryOrder order, String newStatus, String logText) throws SQLException {
        if (orderDao.changeStatus(staff, order, newStatus, logText)) {
            System.out.println("Order " + order.orderId() + " is now " + newStatus + ".");
        } else {
            System.out.println("The status was not changed. The order may have been updated by someone else.");
        }
    }
}