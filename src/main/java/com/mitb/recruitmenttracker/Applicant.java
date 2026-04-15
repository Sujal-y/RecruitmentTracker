package com.mitb.recruitmenttracker;

import javafx.beans.property.*;

public class Applicant {
    private final int applicantId;
    private final int currentStageId;
    private final StringProperty name;
    private final StringProperty email;
    private final StringProperty overallStatus;
    private final StringProperty currentStage;
    private final IntegerProperty roundsCompleted;
    private final DoubleProperty avgScore;

    public Applicant(int applicantId, int currentStageId, String name, String email, String overallStatus,
                     String currentStage, int roundsCompleted, double avgScore) {
        this.applicantId = applicantId;
        this.currentStageId = currentStageId;
        this.name = new SimpleStringProperty(name);
        this.email = new SimpleStringProperty(email != null ? email : "");
        this.overallStatus = new SimpleStringProperty(overallStatus != null ? overallStatus : "");
        this.currentStage = new SimpleStringProperty(currentStage);
        this.roundsCompleted = new SimpleIntegerProperty(roundsCompleted);
        this.avgScore = new SimpleDoubleProperty(avgScore);
    }

    public int getApplicantId() {
        return applicantId;
    }

    public int getCurrentStageId() {
        return currentStageId;
    }

    public String getName() {
        return name.get();
    }

    public String getEmail() {
        return email.get();
    }

    public String getOverallStatus() {
        return overallStatus.get();
    }

    public String getCurrentStage() {
        return currentStage.get();
    }

    public int getRoundsCompleted() {
        return roundsCompleted.get();
    }

    public double getAvgScore() {
        return avgScore.get();
    }
}
