package com.mitb.recruitmenttracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 1. This line finds your FXML file
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));

        // 2. This line "inflates" the XML into a Java window
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);

        // 3. This line actually opens the window
        stage.setTitle("Recruitment Tracker - Sujal");
        stage.setScene(scene);
        stage.show();
    }

}
