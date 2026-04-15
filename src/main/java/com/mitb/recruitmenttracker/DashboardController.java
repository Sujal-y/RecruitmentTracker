package com.mitb.recruitmenttracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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
        colScore.setCellFactory(col -> new TableCell<Applicant, Double>() {
            private final HBox box = new HBox(8);
            private final ProgressBar bar = new ProgressBar();
            private final Label lbl = new Label();
            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                bar.setPrefWidth(60);
                bar.setPrefHeight(10);
                bar.setStyle("-fx-accent: #3b82f6; -fx-control-inner-background: #e5e7eb;");
                lbl.setStyle("-fx-text-fill: #374151; -fx-font-weight: bold;");
                box.getChildren().addAll(bar, lbl);
            }
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    double max = 0.0;
                    for (Applicant a : getTableView().getItems()) {
                        if (a.getAvgScore() > max) max = a.getAvgScore();
                    }
                    if (max == 0) max = 10.0;
                    bar.setProgress(item / max);
                    lbl.setText(String.format("%.1f", item));
                    setGraphic(box);
                }
            }
        });

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
        applicantTable.setRowFactory(table -> {
            TableRow<Applicant> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openApplicantProfileDialog(row.getItem());
                }
            });
            return row;
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

    private void openApplicantProfileDialog(Applicant applicant) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Applicant Profile");
        dialog.setHeaderText("Edit applicant details");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField(applicant.getName());
        TextField emailField = new TextField(applicant.getEmail());
        ComboBox<String> statusField = new ComboBox<>(FXCollections.observableArrayList(
                "Pending", "Active", "Rejected", "Hired"
        ));
        if (applicant.getOverallStatus() != null && !applicant.getOverallStatus().isBlank()) {
            statusField.getSelectionModel().select(applicant.getOverallStatus());
        }
        if (statusField.getSelectionModel().getSelectedItem() == null) {
            statusField.getSelectionModel().select("Pending");
        }
        final String[] selectedPhotoPath = {loadProfilePicPath(applicant.getApplicantId())};
        final int[] selectedRotation = {0};
        ImageView profileImageView = new ImageView();
        profileImageView.setFitWidth(120);
        profileImageView.setFitHeight(120);
        profileImageView.setPreserveRatio(true);
        profileImageView.setSmooth(true);
        profileImageView.setImage(resolveProfileImage(selectedPhotoPath[0]));
        Button uploadPhotoButton = new Button("Upload photo");
        Button rotateLeftButton = new Button("Rotate Left");
        Button rotateRightButton = new Button("Rotate Right");
        rotateLeftButton.setVisible(false);
        rotateLeftButton.setManaged(false);
        rotateRightButton.setVisible(false);
        rotateRightButton.setManaged(false);
        uploadPhotoButton.setOnAction(evt -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select profile photo");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
            );
            File picked = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (picked != null) {
                String managedPath = copyPhotoToProject(picked.toPath());
                if (managedPath == null) {
                    showInfo("Could not save photo into project resources.");
                    return;
                }
                selectedPhotoPath[0] = managedPath;
                selectedRotation[0] = 0;
                profileImageView.setRotate(0);
                profileImageView.setImage(resolveProfileImage(selectedPhotoPath[0]));
                rotateLeftButton.setVisible(true);
                rotateLeftButton.setManaged(true);
                rotateRightButton.setVisible(true);
                rotateRightButton.setManaged(true);
            }
        });
        rotateLeftButton.setOnAction(evt -> {
            selectedRotation[0] = Math.floorMod(selectedRotation[0] - 90, 360);
            profileImageView.setRotate(selectedRotation[0]);
        });
        rotateRightButton.setOnAction(evt -> {
            selectedRotation[0] = Math.floorMod(selectedRotation[0] + 90, 360);
            profileImageView.setRotate(selectedRotation[0]);
        });
        Button deletePhotoButton = new Button("Delete photo");
        deletePhotoButton.setOnAction(evt -> {
            selectedPhotoPath[0] = "";
            selectedRotation[0] = 0;
            profileImageView.setRotate(0);
            profileImageView.setImage(createDefaultProfileImage());
            rotateLeftButton.setVisible(false);
            rotateLeftButton.setManaged(false);
            rotateRightButton.setVisible(false);
            rotateRightButton.setManaged(false);
        });
        HBox photoPickerRow = new HBox(10, uploadPhotoButton, rotateLeftButton, rotateRightButton, deletePhotoButton);
        ComboBox<InterviewRound> stageField = new ComboBox<>(FXCollections.observableArrayList(roundsCache));
        InterviewRound currentRound = roundsCache.stream()
                .filter(r -> r.roundId() == applicant.getCurrentStageId())
                .findFirst()
                .orElse(null);
        stageField.getSelectionModel().select(currentRound);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Name"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Status"), 0, 2);
        grid.add(statusField, 1, 2);
        grid.add(new Label("Profile photo"), 0, 3);
        grid.add(profileImageView, 1, 3);
        grid.add(new Label("Photo actions"), 0, 4);
        grid.add(photoPickerRow, 1, 4);
        grid.add(new Label("Current stage"), 0, 6);
        grid.add(stageField, 1, 6);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> response = dialog.showAndWait();
        if (response.isEmpty() || response.get() != ButtonType.OK) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String status = statusField.getSelectionModel().getSelectedItem();
        String profilePath = selectedPhotoPath[0] == null ? "" : selectedPhotoPath[0].trim();
        InterviewRound selectedRound = stageField.getSelectionModel().getSelectedItem();

        if (name.isEmpty() || email.isEmpty() || status == null || status.isEmpty()) {
            showInfo("Name, email, and status cannot be empty.");
            return;
        }
        if (!email.contains("@")) {
            showInfo("Enter a valid email address.");
            return;
        }
        if (name.length() > 50 || email.length() > 50 || status.length() > 20 || profilePath.length() > 255) {
            showInfo("Field lengths exceed database limits.");
            return;
        }
        if (selectedRound == null) {
            showInfo("Select a stage.");
            return;
        }
        if (!profilePath.isBlank() && selectedRotation[0] != 0) {
            String rotatedPath = rotateAndPersistImage(profilePath, selectedRotation[0]);
            if (rotatedPath == null) {
                showInfo("Could not rotate selected photo.");
                return;
            }
            profilePath = rotatedPath;
        }

        String updateSql = """
            UPDATE APPLICANT
            SET name = ?, email = ?, overall_status = ?, profile_pic_path = ?, current_stage_id = ?
            WHERE applicant_id = ?
            """;
        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, status);
            ps.setString(4, profilePath.isBlank() ? null : profilePath);
            ps.setInt(5, selectedRound.roundId());
            ps.setInt(6, applicant.getApplicantId());
            ps.executeUpdate();
            refreshApplicants();
            reselectApplicant(applicant.getApplicantId());
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                showInfo("That email is already used by another applicant.");
            } else {
                showError("Could not update applicant profile", e);
            }
        }
    }

    private String loadProfilePicPath(int applicantId) {
        String sql = "SELECT profile_pic_path FROM APPLICANT WHERE applicant_id = ?";
        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, applicantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("profile_pic_path");
                    return value == null ? "" : value;
                }
            }
        } catch (SQLException e) {
            showError("Could not load applicant profile", e);
        }
        return "";
    }

    private static Image resolveProfileImage(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isBlank()) {
            return createDefaultProfileImage();
        }
        try {
            Image image;
            if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:/")) {
                image = new Image(path, false);
            } else {
                Path candidate = Path.of(path);
                if (!candidate.isAbsolute()) {
                    candidate = Path.of(System.getProperty("user.dir")).resolve(path).normalize();
                }
                image = new Image(candidate.toUri().toString(), false);
            }
            if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return createDefaultProfileImage();
            }
            return image;
        } catch (Exception ignored) {
            return createDefaultProfileImage();
        }
    }

    private static Image createDefaultProfileImage() {
        int size = 120;
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean border = x < 3 || y < 3 || x >= size - 3 || y >= size - 3;
                int argb = border ? 0xFF9CA3AF : 0xFFE5E7EB;
                pw.setArgb(x, y, argb);
            }
        }
        for (int i = 0; i < size; i++) {
            pw.setArgb(i, i, 0xFFB0B7C3);
            pw.setArgb(size - 1 - i, i, 0xFFB0B7C3);
        }
        return img;
    }

    private static String copyPhotoToProject(Path sourcePath) {
        try {
            String fileName = sourcePath.getFileName().toString();
            String extension = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx >= 0) {
                extension = fileName.substring(dotIdx).toLowerCase(Locale.ROOT);
            }
            if (extension.isBlank()) {
                extension = ".png";
            }
            Path projectRoot = Path.of(System.getProperty("user.dir"));
            Path destDir = projectRoot.resolve("src").resolve("main").resolve("resources").resolve("profile-pics");
            Files.createDirectories(destDir);
            String uniqueName = "applicant-" + UUID.randomUUID() + extension;
            Path destPath = destDir.resolve(uniqueName);
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            return "src/main/resources/profile-pics/" + uniqueName;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String rotateAndPersistImage(String rawPath, int rotationDegrees) {
        try {
            Path source = Path.of(rawPath);
            if (!source.isAbsolute()) {
                source = Path.of(System.getProperty("user.dir")).resolve(source).normalize();
            }
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                return null;
            }
            BufferedImage rotated = rotateImage(image, rotationDegrees);
            Path projectRoot = Path.of(System.getProperty("user.dir"));
            Path destDir = projectRoot.resolve("src").resolve("main").resolve("resources").resolve("profile-pics");
            Files.createDirectories(destDir);
            String uniqueName = "applicant-" + UUID.randomUUID() + ".png";
            Path destPath = destDir.resolve(uniqueName);
            ImageIO.write(rotated, "png", destPath.toFile());
            return "src/main/resources/profile-pics/" + uniqueName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static BufferedImage rotateImage(BufferedImage input, int rotationDegrees) {
        int degrees = Math.floorMod(rotationDegrees, 360);
        if (degrees == 0) {
            return input;
        }
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int w = input.getWidth();
        int h = input.getHeight();
        int newW = (int) Math.floor(w * cos + h * sin);
        int newH = (int) Math.floor(h * cos + w * sin);
        BufferedImage rotated = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        AffineTransform at = new AffineTransform();
        at.translate(newW / 2.0, newH / 2.0);
        at.rotate(radians);
        at.translate(-w / 2.0, -h / 2.0);
        g2d.drawRenderedImage(input, at);
        g2d.dispose();
        return rotated;
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
