package com.scc.model;

/** One laundry order. totalAmount is null until the staff enters the price. */
public record LaundryOrder(int orderId, int customerId, String customerName, String serviceType, String orderDate,
                           Double totalAmount, String orderStatus, String lastUpdated, String notes) {

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_PROCESSING = "Processing";
    public static final String STATUS_READY = "Ready";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_CANCELLED = "Cancelled";

    public static final String[] ALL_STATUSES = {
            STATUS_PENDING, STATUS_PROCESSING, STATUS_READY, STATUS_COMPLETED, STATUS_CANCELLED
    };

    public static final String[] SERVICE_TYPES = {
            "Wash and Dry", "Wash, Dry, and Fold", "Ironing", "Dry Cleaning"
    };

    // Completed and Cancelled orders are history: they cannot be changed anymore.
    public boolean isFinished() {
        return orderStatus.equals(STATUS_COMPLETED) || orderStatus.equals(STATUS_CANCELLED);
    }

    // Shows "Not set" while the staff has not entered the price yet.
    public String amountText() {
        return totalAmount == null ? "Not set" : String.format("%.2f", totalAmount);
    }

    // Pending -> Processing -> Ready -> Completed, and Pending -> Cancelled.
    public static String[] allowedNextStatuses(String status) {
        switch (status) {
            case STATUS_PENDING:
                return new String[] {STATUS_PROCESSING, STATUS_CANCELLED};
            case STATUS_PROCESSING:
                return new String[] {STATUS_READY};
            case STATUS_READY:
                return new String[] {STATUS_COMPLETED};
            default:
                return new String[0]; // Completed and Cancelled are final
        }
    }

    public static boolean isValidTransition(String from, String to) {
        for (String allowed : allowedNextStatuses(from)) {
            if (allowed.equals(to)) {
                return true;
            }
        }
        return false;
    }
}