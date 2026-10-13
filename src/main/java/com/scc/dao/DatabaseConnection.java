package com.scc.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Opens SQLite connections and creates the tables (the same tables are in the SQL script).
 * No connection is kept open: every DAO method opens its own and closes it.
 */
public class DatabaseConnection {
    // The database file is created in the project folder the first time the program runs.
    private static final String DB_URL = "jdbc:sqlite:order_tracker.db";
    private static final String DB_FILE = "order_tracker.db";

    public static String getDatabasePath() {
        return DB_FILE;
    }

    public static long getDatabaseFileSize() {
        File file = new File(DB_FILE);
        return file.exists() ? file.length() : 0;
    }

    // Opens a new connection and turns on foreign-key checking (SQLite has it OFF by default).
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    // Current date and time as text, for example 2026-10-11 14:30:05
    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // Returns the ID of the row that was just inserted on this same connection.
    public static int lastInsertId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Creates the tables if they do not exist yet.
    public static void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "full_name TEXT NOT NULL, "
                    + "contact_number TEXT NOT NULL, "
                    + "address TEXT NOT NULL, "
                    + "username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "password_hash TEXT NOT NULL, "
                    + "role TEXT NOT NULL CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN')), "
                    + "account_status TEXT NOT NULL DEFAULT 'PENDING' "
                    + "CHECK (account_status IN ('PENDING', 'ACTIVE', 'INACTIVE')), "
                    + "created_at TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS laundry_orders ("
                    + "order_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "customer_id INTEGER NOT NULL REFERENCES users(user_id), "
                    + "service_type TEXT NOT NULL CHECK (service_type IN "
                    + "('Wash and Dry', 'Wash, Dry, and Fold', 'Ironing', 'Dry Cleaning')), "
                    + "order_date TEXT NOT NULL, "
                    + "total_amount REAL CHECK (total_amount IS NULL OR total_amount >= 0), "
                    + "order_status TEXT NOT NULL DEFAULT 'Pending' CHECK (order_status IN "
                    + "('Pending', 'Processing', 'Ready', 'Completed', 'Cancelled')), "
                    + "last_updated TEXT NOT NULL, "
                    + "notes TEXT)");

            st.execute("CREATE TABLE IF NOT EXISTS laundry_activity_logs ("
                    + "log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL REFERENCES users(user_id), "
                    + "action TEXT NOT NULL, "
                    + "order_id INTEGER REFERENCES laundry_orders(order_id), "
                    + "timestamp TEXT NOT NULL)");
        }
    }
}