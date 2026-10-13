package com.scc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The numbers of the Laundry Order Status Report. The console, CSV, PDF, and XLSX
 * all read the same rows from here, so every format shows the same information.
 * fromDate / toDate are null when no date filter was used.
 */
public record OrderStatusReport(String generatedAt, String fromDate, String toDate, int total,
                                int pending, int processing, int ready, int completed, int cancelled) {

    public static final String REPORT_NAME = "Laundry Order Status Report";

    public String dateRangeText() {
        if (fromDate == null && toDate == null) {
            return "All dates";
        }
        return (fromDate == null ? "Beginning" : fromDate) + " to " + (toDate == null ? "Today" : toDate);
    }

    // Rows of {label, value} for the report information.
    public List<String[]> infoRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Report Name", REPORT_NAME});
        rows.add(new String[] {"Generated On", generatedAt});
        rows.add(new String[] {"Order Date Range", dateRangeText()});
        return rows;
    }

    // Rows of {status, count} for the summary of order counts.
    public List<String[]> countRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Total Orders", String.valueOf(total)});
        rows.add(new String[] {LaundryOrder.STATUS_PENDING, String.valueOf(pending)});
        rows.add(new String[] {LaundryOrder.STATUS_PROCESSING, String.valueOf(processing)});
        rows.add(new String[] {LaundryOrder.STATUS_READY, String.valueOf(ready)});
        rows.add(new String[] {LaundryOrder.STATUS_COMPLETED, String.valueOf(completed)});
        rows.add(new String[] {LaundryOrder.STATUS_CANCELLED, String.valueOf(cancelled)});
        return rows;
    }
}