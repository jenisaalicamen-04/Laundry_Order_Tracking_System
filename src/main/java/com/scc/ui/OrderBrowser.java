package com.scc.ui;

import com.scc.dao.OrderDao;
import com.scc.model.LaundryOrder;
import com.scc.model.User;
import java.sql.SQLException;

/** View, search, and filter screens for ALL orders. Used by the Staff and Admin menus. */
public class OrderBrowser {
    private final InputHelper input;
    private final OrderDao orderDao;

    public OrderBrowser(InputHelper input, OrderDao orderDao) {
        this.input = input;
        this.orderDao = orderDao;
    }

    public void viewAll(User actor) throws SQLException {
        InputHelper.header("ALL LAUNDRY ORDERS");
        OrderView.printTable(orderDao.findAll(actor), true);
    }

    public void search(User actor) throws SQLException {
        InputHelper.header("SEARCH LAUNDRY ORDERS");
        System.out.println("1. Search by Order ID");
        System.out.println("2. Search by Customer Name");
        System.out.println("3. Filter by Status");
        System.out.println("4. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
        switch (choice) {
            case 1:
                int orderId = input.readInt("Enter Order ID: ");
                LaundryOrder order = orderDao.findById(actor, orderId);
                if (order == null) {
                    System.out.println("Order not found.");
                } else {
                    OrderView.printDetails(order);
                }
                break;
            case 2:
                String name = input.readNonEmpty("Enter customer name (or part of it): ");
                OrderView.printTable(orderDao.searchByCustomerName(actor, name), true);
                break;
            case 3:
                filterByStatus(actor);
                break;
            default:
                break;
        }
    }

    public void filterByStatus(User actor) throws SQLException {
        String status = input.chooseFromList("Choose a status:", LaundryOrder.ALL_STATUSES);
        InputHelper.header("ORDERS WITH STATUS: " + status.toUpperCase());
        OrderView.printTable(orderDao.findByStatus(actor, status), true);
    }
}