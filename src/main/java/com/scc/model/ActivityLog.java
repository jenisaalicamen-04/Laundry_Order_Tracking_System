package com.scc.model;

/** One row of the Laundry Activity Log. orderId is null when the action is not about an order. */
public record ActivityLog(int logId, String username, String userRole, String action, Integer orderId, String timestamp) {
}