package com.mitb.recruitmenttracker;
import javafx.beans.property.*;

public class Applicant {
    private final StringProperty name;
    private final StringProperty currentStage;
    private final IntegerProperty roundsCompleted;
    private final DoubleProperty avgScore;

    public Applicant(String name, String currentStage, int roundsCompleted, double avgScore) {
        this.name = new SimpleStringProperty(name);
        this.currentStage = new SimpleStringProperty(currentStage);
        this.roundsCompleted = new SimpleIntegerProperty(roundsCompleted);
        this.avgScore = new SimpleDoubleProperty(avgScore);
    }

    // These getters are required for the JavaFX TableView to auto-populate
    public String getName() { return name.get(); }
    public String getCurrentStage() { return currentStage.get(); }
    public int getRoundsCompleted() { return roundsCompleted.get(); }
    public double getAvgScore() { return avgScore.get(); }
}
