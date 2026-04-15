package com.mitb.recruitmenttracker;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class LoginController {

    @FXML private TextField adminUserField;
    @FXML private PasswordField adminPassField;
    @FXML private TextField applicantNameField;
    @FXML private TextField applicantEmailField;

    @FXML
    private void onAdminLogin() {
        String user = Optional.ofNullable(adminUserField.getText()).orElse("").trim();
        String pass = adminPassField.getText() != null ? adminPassField.getText() : "";
        String expectedUser = Optional.ofNullable(System.getenv("ADMIN_USER")).orElse("admin");
        String expectedPass = Optional.ofNullable(System.getenv("ADMIN_PASSWORD")).orElse("admin");
        if (!expectedUser.equals(user) || !expectedPass.equals(pass)) {
            showAlert(Alert.AlertType.ERROR, "Sign-in failed", "Invalid username or password.");
            return;
        }
        try {
            Stage stage = (Stage) adminUserField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
            Scene scene = new Scene(loader.load(), 960, 700);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Recruitment Tracker — Admin");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open admin dashboard.");
        }
    }

    @FXML
    private void onApplicantSubmit() {
        String name = Optional.ofNullable(applicantNameField.getText()).orElse("").trim();
        String email = Optional.ofNullable(applicantEmailField.getText()).orElse("").trim();
        if (name.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Application", "Please enter your name.");
            return;
        }
        if (email.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Application", "Please enter your email.");
            return;
        }
        if (!email.contains("@")) {
            showAlert(Alert.AlertType.INFORMATION, "Application", "Please enter a valid email address.");
            return;
        }
        if (name.length() > 50 || email.length() > 50) {
            showAlert(Alert.AlertType.INFORMATION, "Application", "Name and email must be at most 50 characters.");
            return;
        }

        try (Connection conn = DatabaseHandler.getConnection()) {
            int firstRoundId;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT round_id FROM INTERVIEW_ROUND ORDER BY sequence_order LIMIT 1");
                 ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    showAlert(Alert.AlertType.ERROR, "Application",
                            "No interview rounds are configured yet. Please try again later.");
                    return;
                }
                firstRoundId = rs.getInt(1);
            }
            try (PreparedStatement ins = conn.prepareStatement("""
                    INSERT INTO APPLICANT (name, email, current_stage_id, overall_status)
                    VALUES (?, ?, ?, 'Pending')
                    """)) {
                ins.setString(1, name);
                ins.setString(2, email);
                ins.setInt(3, firstRoundId);
                ins.executeUpdate();
            }
            applicantNameField.clear();
            applicantEmailField.clear();
            showAlert(Alert.AlertType.INFORMATION, "Application submitted",
                    "Thank you. Your application is pending review by an administrator.");
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                showAlert(Alert.AlertType.INFORMATION, "Application", "That email is already registered.");
            } else {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Application", e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
    }

    private static void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
