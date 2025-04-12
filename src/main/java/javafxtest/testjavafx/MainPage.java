package javafxtest.testjavafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class MainPage extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("MainPageUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 250);
        scene.getStylesheets().add(getClass().getResource("MainPageUI.css").toExternalForm());
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}