package com.mitb.recruitmenttracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHandler {
    private static final String URL = "jdbc:postgresql://localhost:5432/RecruitmentTracker";
    private static final String USER = "postgres";
    private static final String PASS = "SYSTEM";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
