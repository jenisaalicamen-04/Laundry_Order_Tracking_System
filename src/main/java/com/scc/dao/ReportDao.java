package com.scc.dao;

import com.scc.model.LaundryOrder;
import com.scc.model.OrderStatusReport;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the Laundry Order Status Report and the admin's System Records. Admin only. */
public class ReportDao {

    // fromDate / toDate are yyyy-MM-dd text or null. The counts are calculated by the database.
    public OrderStatusReport buildReport(User actor, String fromDate, String toDate) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<String> params = new ArrayList<>();
        if (fromDate != null) {
            where.append(" AND substr(order_date, 1, 10) >= ?");
            params.add(fromDate);
        }
        if (toDate != null) {
            where.append(" AND substr(order_date, 1, 10) <= ?");
            params.add(toDate);
        }

        int total;
        int pending = 0;
        int processing = 0;
        int ready = 0;
        int completed = 0;
        int cancelled = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Total number of orders (cancelled orders are included)
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM laundry_orders" + where)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            // Count per status. GROUP BY counts every order exactly once.
            String sql = "SELECT order_status, COUNT(*) FROM laundry_orders" + where + " GROUP BY order_status";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String status = rs.getString(1);
                        int count = rs.getInt(2);
                        switch (status) {
                            case LaundryOrder.STATUS_PENDING:
                                pending = count;
                                break;
                            case LaundryOrder.STATUS_PROCESSING:
                                processing = count;
                                break;
                            case LaundryOrder.STATUS_READY:
                                ready = count;
                                break;
                            case LaundryOrder.STATUS_COMPLETED:
                                completed = count;
                                break;
                            case LaundryOrder.STATUS_CANCELLED:
                                cancelled = count;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }
        return new OrderStatusReport(DatabaseConnection.now(), fromDate, toDate, total,
                pending, processing, ready, completed, cancelled);
    }

    private void bind(PreparedStatement ps, List<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setString(i + 1, params.get(i));
        }
    }

    // ---------- System records ----------

    // Rows of {role, status, count}
    public List<String[]> countUsersByRoleAndStatus(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT role, account_status, COUNT(*) FROM users GROUP BY role, account_status "
                + "ORDER BY role, account_status", 3);
    }

    // Rows of {status, count}
    public List<String[]> countOrdersByStatus(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT order_status, COUNT(*) FROM laundry_orders GROUP BY order_status ORDER BY order_status", 2);
    }

    // {number of log entries, latest log time (or null)}
    public String[] logSummary(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT COUNT(*), MAX(timestamp) FROM laundry_activity_logs", 2).get(0);
    }

    private List<String[]> rows(String sql, int columns) throws SQLException {
        List<String[]> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] row = new String[columns];
                for (int i = 0; i < columns; i++) {
                    row[i] = rs.getString(i + 1);
                }
                result.add(row);
            }
        }
        return result;
    }
}