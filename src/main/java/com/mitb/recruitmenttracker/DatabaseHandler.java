package com.mitb.recruitmenttracker;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHandler {
    // Database login credentials
    private static final String URL = "jdbc:postgresql://localhost:5432/RecruitmentTracker";
    private static final String USER = "postgres";

    // This looks for a variable on your system instead of a hardcoded string
    private static final String PASS = System.getenv("DB_PASS");

    public static Connection getConnection() throws SQLException {
        if (PASS == null) {
            throw new SQLException("DB_PASS environment variable is not set!");
        }
        // This connects Java to your PostgreSQL instance
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
