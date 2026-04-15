package com.mitb.recruitmenttracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.sql.*;

public class DashboardController {

    // These IDs must match the fx:id we will set in the FXML file
    @FXML private TableView<Applicant> applicantTable;
    @FXML private TableColumn<Applicant, String> colName;
    @FXML private TableColumn<Applicant, String> colStage;
    @FXML private TableColumn<Applicant, Integer> colRounds;
    @FXML private TableColumn<Applicant, Double> colScore;

    // This list holds our Applicant objects in memory for the UI
    private ObservableList<Applicant> applicantList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Step A: Link columns to the Applicant class getters
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colStage.setCellValueFactory(new PropertyValueFactory<>("currentStage"));
        colRounds.setCellValueFactory(new PropertyValueFactory<>("roundsCompleted"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("avgScore"));

        // Step B: Fetch the data
        loadDataFromPostgres();

        // This "Listener" detects whenever you click a row in the table
        applicantTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                System.out.println("Selected Candidate: " + newSelection.getName());
                // This is where we will trigger the Profile view or Update logic
            }
        });
    }

    private void loadDataFromPostgres() {
        applicantList.clear(); // Ensure we don't double-up data on refresh

        String query = """
            SELECT a.name, r.round_name, 
                   COUNT(CASE WHEN p.status = 'Passed' THEN 1 END) AS rounds,
                   ROUND(AVG(p.compatibility_score), 2) AS score
            FROM APPLICANT a
            JOIN INTERVIEW_ROUND r ON a.current_stage_id = r.round_id
            LEFT JOIN INTERVIEW_PROGRESS p ON a.applicant_id = p.applicant_id
            GROUP BY a.applicant_id, a.name, r.round_name
            ORDER BY score DESC NULLS LAST;
            """;

        try (Connection conn = DatabaseHandler.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // This is where you fill your "buckets"
                applicantList.add(new Applicant(
                        rs.getString("name"),
                        rs.getString("round_name"),
                        rs.getInt("rounds"),
                        rs.getDouble("score")
                ));
            }

            // Step C: Hand the list to the TableView
            applicantTable.setItems(applicantList);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}