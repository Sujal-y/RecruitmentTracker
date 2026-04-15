package com.mitb.recruitmenttracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DashboardController {

    @FXML private TableView<Applicant> applicantTable;
    @FXML private TableColumn<Applicant, String> colName;
    @FXML private TableColumn<Applicant, String> colEmail;
    @FXML private TableColumn<Applicant, String> colStatus;
    @FXML private TableColumn<Applicant, String> colStage;
    @FXML private TableColumn<Applicant, Integer> colRounds;
    @FXML private TableColumn<Applicant, Double> colScore;

    @FXML private TextField newNameField;
    @FXML private TextField newEmailField;
    @FXML private ComboBox<InterviewRound> newApplicantStageCombo;
    @FXML private ComboBox<InterviewRound> updateStageCombo;
    @FXML private Spinner<Integer> stageScoreSpinner;
    @FXML private Button updateStageButton;
    @FXML private Button deleteApplicantButton;

    private final ObservableList<Applicant> applicantList = FXCollections.observableArrayList();
    private final List<InterviewRound> roundsCache = new ArrayList<>();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("overallStatus"));
        colStage.setCellValueFactory(new PropertyValueFactory<>("currentStage"));
        colRounds.setCellValueFactory(new PropertyValueFactory<>("roundsCompleted"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("avgScore"));

        stageScoreSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10, 7, 1));

        loadRoundsIntoCombos();
        refreshApplicants();

        applicantTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean has = selected != null;
            updateStageButton.setDisable(!has);
            deleteApplicantButton.setDisable(!has);
            if (selected != null) {
                int currentId = selected.getCurrentStageId();
                for (int i = 0; i < roundsCache.size(); i++) {
                    if (roundsCache.get(i).roundId() == currentId) {
                        if (i + 1 < roundsCache.size()) {
                            updateStageCombo.getSelectionModel().select(roundsCache.get(i + 1));
                        } else {
                            updateStageCombo.getSelectionModel().select(roundsCache.get(i));
                        }
                        break;
                    }
                }
            } else {
                updateStageCombo.getSelectionModel().clearSelection();
            }
        });

        updateStageButton.setDisable(true);
        deleteApplicantButton.setDisable(true);
    }

    private void loadRoundsIntoCombos() {
        roundsCache.clear();
        String sql = "SELECT round_id, round_name FROM INTERVIEW_ROUND ORDER BY sequence_order";
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                roundsCache.add(new InterviewRound(rs.getInt("round_id"), rs.getString("round_name")));
            }
        } catch (SQLException e) {
            showError("Could not load interview rounds", e);
            return;
        }

        ObservableList<InterviewRound> items = FXCollections.observableArrayList(roundsCache);
        newApplicantStageCombo.setItems(items);
        updateStageCombo.setItems(FXCollections.observableArrayList(roundsCache));

        if (!roundsCache.isEmpty()) {
            newApplicantStageCombo.getSelectionModel().selectFirst();
        }
    }

    private void refreshApplicants() {
        applicantList.clear();

        String query = """
            SELECT a.applicant_id, a.current_stage_id, a.name, a.email, a.overall_status, r.round_name,
                   COUNT(CASE WHEN p.status = 'Passed' THEN 1 END) AS rounds,
                   ROUND(AVG(p.compatibility_score)::numeric, 2) AS score
            FROM APPLICANT a
            JOIN INTERVIEW_ROUND r ON a.current_stage_id = r.round_id
            LEFT JOIN INTERVIEW_PROGRESS p ON a.applicant_id = p.applicant_id
            GROUP BY a.applicant_id, a.current_stage_id, a.name, a.email, a.overall_status, r.round_name
            ORDER BY score DESC NULLS LAST;
            """;

        try (Connection conn = DatabaseHandler.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                double score = rs.getDouble("score");
                if (rs.wasNull()) {
                    score = 0.0;
                }
                String email = rs.getString("email");
                if (email == null) {
                    email = "";
                }
                String overallStatus = rs.getString("overall_status");
                if (overallStatus == null) {
                    overallStatus = "";
                }
                applicantList.add(new Applicant(
                        rs.getInt("applicant_id"),
                        rs.getInt("current_stage_id"),
                        rs.getString("name"),
                        email,
                        overallStatus,
                        rs.getString("round_name"),
                        rs.getInt("rounds"),
                        score
                ));
            }

            applicantTable.setItems(applicantList);

        } catch (SQLException e) {
            showError("Could not load applicants", e);
        }
    }

    @FXML
    private void onAddApplicant() {
        String name = newNameField.getText() == null ? "" : newNameField.getText().trim();
        if (name.isEmpty()) {
            showInfo("Enter a name for the new applicant.");
            return;
        }
        String email = newEmailField.getText() == null ? "" : newEmailField.getText().trim();
        if (email.isEmpty()) {
            showInfo("Enter an email for the new applicant.");
            return;
        }
        if (!email.contains("@")) {
            showInfo("Enter a valid email address (must contain @).");
            return;
        }
        if (name.length() > 50 || email.length() > 50) {
            showInfo("Name and email must be at most 50 characters (database limit).");
            return;
        }
        InterviewRound stage = newApplicantStageCombo.getSelectionModel().getSelectedItem();
        if (stage == null) {
            showInfo("Select a starting stage (add rounds to INTERVIEW_ROUND if this list is empty).");
            return;
        }

        String sql = """
            INSERT INTO APPLICANT (name, email, current_stage_id)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setInt(3, stage.roundId());
            ps.executeUpdate();
            newNameField.clear();
            newEmailField.clear();
            refreshApplicants();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                showInfo("That email is already registered.");
            } else {
                showError("Could not add applicant", e);
            }
        }
    }

    @FXML
    private void onUpdateStage() {
        Applicant selected = applicantTable.getSelectionModel().getSelectedItem();
        InterviewRound newStage = updateStageCombo.getSelectionModel().getSelectedItem();
        if (selected == null || newStage == null) {
            showInfo("Select an applicant and a stage.");
            return;
        }
        if (newStage.roundId() == selected.getCurrentStageId()) {
            showInfo("That stage is already the current stage for this applicant.");
            return;
        }

        int score;
        try {
            score = stageScoreSpinner.getValue();
        } catch (Exception ex) {
            showInfo("Enter a compatibility score between 0 and 10.");
            return;
        }
        if (score < 0 || score > 10) {
            showInfo("Compatibility score must be between 0 and 10.");
            return;
        }

        int applicantId = selected.getApplicantId();
        int currentRoundId = selected.getCurrentStageId();
        int newRoundId = newStage.roundId();

        String upsertProgress = """
            INSERT INTO INTERVIEW_PROGRESS (applicant_id, round_id, compatibility_score, status)
            VALUES (?, ?, ?, 'Passed')
            ON CONFLICT (applicant_id, round_id)
            DO UPDATE SET
                compatibility_score = EXCLUDED.compatibility_score,
                status = 'Passed',
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = DatabaseHandler.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(upsertProgress)) {
                    ps.setInt(1, applicantId);
                    ps.setInt(2, currentRoundId);
                    ps.setInt(3, score);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE APPLICANT SET current_stage_id = ? WHERE applicant_id = ?")) {
                    ps.setInt(1, newRoundId);
                    ps.setInt(2, applicantId);
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        showInfo("No row was updated (applicant may have been removed).");
                        conn.rollback();
                        return;
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            refreshApplicants();
            reselectApplicant(applicantId);
        } catch (SQLException e) {
            showError("Could not update stage", e);
        }
    }

    @FXML
    private void onLogout() {
        try {
            Stage stage = (Stage) applicantTable.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("login.fxml"));
            stage.setScene(new Scene(loader.load(), 500, 460));
            stage.setTitle("Recruitment Tracker");
        } catch (IOException e) {
            e.printStackTrace();
            showError("Could not return to sign-in", e);
        }
    }

    private void reselectApplicant(int applicantId) {
        for (Applicant a : applicantList) {
            if (a.getApplicantId() == applicantId) {
                applicantTable.getSelectionModel().select(a);
                return;
            }
        }
    }

    @FXML
    private void onDeleteApplicant() {
        Applicant selected = applicantTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Select an applicant to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete applicant");
        confirm.setHeaderText("Delete " + selected.getName() + "?");
        confirm.setContentText("This removes the applicant and their interview progress rows.");
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }

        int id = selected.getApplicantId();
        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM APPLICANT WHERE applicant_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            refreshApplicants();
        } catch (SQLException e) {
            showError("Could not delete applicant", e);
        }
    }

    private static void showError(String message, Exception e) {
        e.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Database error");
        a.setHeaderText(message);
        a.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
        a.showAndWait();
    }

    private static void showInfo(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Recruitment Tracker");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}
