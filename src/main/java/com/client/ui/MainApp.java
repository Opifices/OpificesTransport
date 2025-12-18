package com.client.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/com/client/ui/MainWindow.fxml"));
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(MainApp.class.getResource("/com/client/ui/styles.css").toExternalForm());

        stage.setTitle("Modern Torrent Client");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        try {
            com.client.core.memory.HyperLinkAllocator.allocateTensorBuffer();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to initialize Shared Memory: " + e.getMessage());
        }
        launch();
    }
}
