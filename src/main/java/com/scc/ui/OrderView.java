package com.scc.ui;

import com.scc.model.LaundryOrder;
import java.util.List;

/** Prints orders as a table or as a detail card. */
public class OrderView {

    public static void printTable(List<LaundryOrder> orders, boolean showCustomer) {
        if (orders.isEmpty()) {
            System.out.println("No orders found.");
            return;
        }
        String format = showCustomer
                ? "%-6s %-18s %-20s %-19s %-9s %-11s %s%n"
                : "%-6s %-20s %-19s %-9s %-11s %s%n";
        if (showCustomer) {
            System.out.printf(format, "ID", "Customer", "Service", "Order Date", "Total", "Status", "Last Updated");
        } else {
            System.out.printf(format, "ID", "Service", "Order Date", "Total", "Status", "Last Updated");
        }
        InputHelper.printDivider();
        for (LaundryOrder o : orders) {
            if (showCustomer) {
                System.out.printf(format, o.orderId(), InputHelper.cut(o.customerName(), 18), o.serviceType(),
                        o.orderDate(), o.amountText(), o.orderStatus(), o.lastUpdated());
            } else {
                System.out.printf(format, o.orderId(), o.serviceType(), o.orderDate(),
                        o.amountText(), o.orderStatus(), o.lastUpdated());
            }
        }
        InputHelper.printDivider();
        System.out.println("Orders shown: " + orders.size());
    }

    public static void printDetails(LaundryOrder o) {
        System.out.println();
        System.out.println("Order ID: " + o.orderId());
        System.out.println("Customer: " + o.customerName());
        System.out.println("Service Type: " + o.serviceType());
        System.out.println("Order Date: " + o.orderDate());
        System.out.println("Total Amount: " + o.amountText());
        System.out.println("Current Status: " + o.orderStatus());
        System.out.println("Last Updated: " + o.lastUpdated());
        if (o.notes() != null && !o.notes().isEmpty()) {
            System.out.println("Notes: " + o.notes());
        }
        InputHelper.printDivider();
    }
}