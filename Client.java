package test1;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;

public class Client extends Application {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 1234;

    private BufferedReader in;
    private PrintWriter out;
    private Socket socket;
    private TextArea messageArea;
    private TextField inputField;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            VBox vbox = new VBox();
            messageArea = new TextArea();
            messageArea.setEditable(false);
            inputField = new TextField();

            Button sendButton = new Button("Envoyer");
            sendButton.setOnAction(e -> sendMessage());

            vbox.getChildren().addAll(messageArea, inputField, sendButton);

            Scene scene = new Scene(vbox, 400, 300);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Client");
            primaryStage.show();

            // Lancer un thread pour recevoir les messages
            new Thread(this::receiveMessages).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            out.println(message);
            inputField.clear();
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                // Afficher le message dans la zone de texte
                messageArea.appendText("Reçu : " + message + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
