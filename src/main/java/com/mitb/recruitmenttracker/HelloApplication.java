package com.mitb.recruitmenttracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 1. This line finds your FXML file
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("login.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 500, 460);
        scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());

        stage.setTitle("Recruitment Tracker");
        stage.setScene(scene);
        stage.show();
    }

}
