package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.DatabaseConnection;
import com.scc.dao.ReportDao;
import com.scc.model.OrderStatusReport;
import com.scc.model.User;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Admin screens: Laundry Order Status Report, report export, and system records. */
public class ReportMenu {
    private final InputHelper input;
    private final ReportDao reportDao = new ReportDao();
    private final ActivityLogDao logDao = new ActivityLogDao();
    private final ReportExporter exporter = new ReportExporter();

    public ReportMenu(InputHelper input) {
        this.input = input;
    }

    // ---------- Console report ----------

    public void viewReport(User actor) {
        InputHelper.header("LAUNDRY ORDER STATUS REPORT");
        try {
            String[] range = askDateRange();
            if (range == null) {
                return;
            }
            OrderStatusReport report = reportDao.buildReport(actor, range[0], range[1]);
            System.out.println();
            for (String[] row : report.infoRows()) {
                System.out.println(String.format("%-18s: %s", row[0], row[1]));
            }
            InputHelper.printDivider();
            System.out.println(String.format("%-18s  %s", "Order Status", "Number of Orders"));
            InputHelper.printDivider();
            for (String[] row : report.countRows()) {
                System.out.println(String.format("%-18s  %s", row[0], row[1]));
            }
            InputHelper.printDivider();
            logDao.addLog(actor.userId(), "Report generated: " + OrderStatusReport.REPORT_NAME, null);
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        }
    }

    // ---------- Export ----------

    public void exportReport(User actor) {
        InputHelper.header("EXPORT REPORT");
        System.out.println("1. PDF");
        System.out.println("2. CSV");
        System.out.println("3. Excel (XLSX)");
        System.out.println("4. Back");
        int choice = input.readMenuChoice("Choose the export format: ", 1, 4);
        if (choice == 4) {
            return;
        }
        String format = choice == 1 ? "PDF" : (choice == 2 ? "CSV" : "XLSX");
        try {
            String[] range = askDateRange();
            if (range == null) {
                return;
            }
            OrderStatusReport report = reportDao.buildReport(actor, range[0], range[1]);
            File file;
            if (choice == 1) {
                file = exporter.exportPdf(report);
            } else if (choice == 2) {
                file = exporter.exportCsv(report);
            } else {
                file = exporter.exportXlsx(report);
            }
            System.out.println();
            System.out.println("Report exported successfully (" + format + ").");
            System.out.println("File: " + file.getAbsolutePath());
            logDao.addLog(actor.userId(),
                    "Report exported: " + OrderStatusReport.REPORT_NAME + " (" + format + ")", null);
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        } catch (IOException e) {
            System.out.println("The " + format + " report could not be exported: " + e.getMessage());
        }
    }

    // Asks if the admin wants a date range. Returns {from, to} (either can be null), or null if the range is wrong.
    private String[] askDateRange() {
        String from = null;
        String to = null;
        if (input.readConfirm("Filter by order date range? (Y/N): ")) {
            System.out.println("Leave a date empty to skip it. Format: yyyy-MM-dd (example: 2026-10-01)");
            from = readOptionalDate("From date: ");
            to = readOptionalDate("To date: ");
            if (from != null && to != null && from.compareTo(to) > 0) {
                System.out.println("The 'From' date is after the 'To' date. Please try again.");
                return null;
            }
        }
        return new String[] {from, to};
    }

    private String readOptionalDate(String prompt) {
        while (true) {
            String text = input.readOptional(prompt);
            if (text.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(text).toString();
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Please use the format yyyy-MM-dd.");
            }
        }
    }

    // ---------- System records ----------

    public void viewSystemRecords(User actor) {
        InputHelper.header("SYSTEM RECORDS");
        try {
            System.out.println("Database file: " + DatabaseConnection.getDatabasePath()
                    + " (" + DatabaseConnection.getDatabaseFileSize() + " bytes)");
            System.out.println();

            System.out.println("User accounts:");
            List<String[]> users = reportDao.countUsersByRoleAndStatus(actor);
            if (users.isEmpty()) {
                System.out.println("  (none)");
            }
            for (String[] row : users) {
                System.out.println(String.format("  %-9s %-9s %s", row[0], row[1], row[2]));
            }

            System.out.println();
            System.out.println("Laundry orders by status:");
            List<String[]> orders = reportDao.countOrdersByStatus(actor);
            if (orders.isEmpty()) {
                System.out.println("  (none)");
            }
            for (String[] row : orders) {
                System.out.println(String.format("  %-11s %s", row[0], row[1]));
            }

            System.out.println();
            String[] logs = reportDao.logSummary(actor);
            System.out.println("Activity log entries: " + logs[0] + (logs[1] == null ? "" : " (latest: " + logs[1] + ")"));
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        }
    }
}