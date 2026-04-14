package com.mitb.recruitmenttracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        try (Connection conn = DatabaseHandler.getConnection()) {
            System.out.println("--- DB Connection Successful! ---");

            String query = "SELECT name FROM APPLICANT LIMIT 1;";
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery(query);

            if (rs.next()) {
                System.out.println("First Applicant found in DB: " + rs.getString("name"));
            }
        } catch (Exception e) {
            System.out.println("Connection failed. Check your password!");
            e.printStackTrace();
        }
    }

}
