package test1;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert.AlertType;
import java.awt.Desktop;
import java.net.*;
import java.io.*;


public class Maincontroller {
    //Champ
    private DataInputStream input;
    private DataOutputStream output;
    private Socket socket;

    @FXML
    private TextArea affichage1;
    @FXML
    private AnchorPane affichage;
    @FXML
    private TextField inputmessage;
    @FXML
    private Button button1;
    @FXML
    private Button button2;
    @FXML
    private Button button3;
    @FXML
    private Button button4;
    @FXML
    private Button button5;
    @FXML
    private VBox vbox;

    //Methodes
    @FXML
    public void aide(ActionEvent event) {
        Alert alerte = new Alert(AlertType.INFORMATION);
        alerte.setTitle("Aide");
        alerte.setHeaderText("Ceci est l'aide");
        alerte.setContentText("Envoyer des messages, des fichiers, ou demarrer une discussion vocale à partir cet application.");
        alerte.showAndWait();
    }

    @FXML
    public void apropos(ActionEvent event) {
        Alert alerte = new Alert(AlertType.INFORMATION);
        alerte.setTitle("Avant-Propos");
        alerte.setHeaderText("Ceci est l'avant-propos");
        alerte.setContentText("MyFriend\nCopyright 2024\nTous droits reservés.");
        alerte.showAndWait();
    }

    @FXML
    public void quiiter(ActionEvent event) {
        Platform.exit();
    }

    @FXML
    public void connexion(ActionEvent event) {
        try {
            //Demande de connexion
            socket =  new Socket("localhost", 5000);

            //Flux entrée et sortie respectivement
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            // Lancer un thread pour recevoir les messages
            new Thread(this::recevoir).start();    

            button1.setDisable(false);
            button2.setDisable(false);
            button3.setDisable(false);
            button5.setDisable(false);
            button4.setDisable(true);
        } catch(Exception e) {
            Alert alerte = new Alert(AlertType.INFORMATION);
            alerte.setContentText("Ca ne s'est pas passé comme prévu.");
            alerte.showAndWait();
        }
    }

    @FXML
    public void envoyer(ActionEvent event) throws IOException{
        try {
            //Envoi de message
            String message = inputmessage.getText();
            if(!(message.isEmpty())) {
                output.writeUTF(message);
                inputmessage.clear();

                //Affichage du message à l'écran de son coté.
                affichage1.appendText("Envoyé : " + message + "\n");
            }


        } catch (Exception e) {
            Alert alerte = new Alert(AlertType.INFORMATION);
            alerte.setContentText("Ca ne s'est pas passé comme prévu.");
            alerte.showAndWait();
        }
    }

    public void recevoir()  {
        try {
            //Reception du message
            String message;
            while ((message = input.readUTF()) != null) {
                if (!(message.equals("fichier"))) {
                    affichage1.appendText("Reçu : "  + message + "\n");
                } else {
                    String nomfichier = input.readUTF();
                    long taillefichier = input.readLong();
                    FileOutputStream fos = new FileOutputStream("reçue_" + nomfichier);
                    byte[] buffer = new byte[4096];
                    int lecture_octet;
                    while (taillefichier > 0 && (lecture_octet = input.read(buffer, 0, (int) Math.min(buffer.length, taillefichier))) != -1) {
                        fos.write(buffer, 0, lecture_octet);
                        taillefichier -= lecture_octet;
                    }
                    fos.close();
                    affichage1.appendText("Reçu : fichier envoyé sous le nom de 'reçue_" + nomfichier + "' dans le repertoire courrant ou se trouve l'application.\n");
                }
            }
        } catch (Exception e) {
            System.out.println("Ohoh");
        } 
    }


    @FXML
    public void envoyerfichier(ActionEvent event) throws IOException{
        FileChooser fileChooser = new FileChooser();
        Stage stage = (Stage) vbox.getScene().getWindow();
        File fichier = fileChooser.showOpenDialog(stage);

        if (fichier != null) {
            try {
                output.writeUTF("fichier");
                output.writeUTF(fichier.getName());
                output.writeLong(fichier.length());
                FileInputStream fis = new FileInputStream(fichier);
                byte[] buffer = new byte[4096];
                int lecture_octet;
                while ((lecture_octet = fis.read(buffer)) != -1) {
                    output.write(buffer, 0, lecture_octet);
                }
                fis.close();
                affichage1.appendText("Envoyé : Fichier envoyé avec succès\n");
            } catch (Exception e) {
                System.out.println("Fail");
            }
        }
    }

    @FXML
    public void effacer(ActionEvent event) {
        affichage1.clear();
    }

    @FXML
    public void lien(ActionEvent event) {
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.browse(new URI("https://github.com/cestbryan-ng"));
        } catch (Exception e) {
            Alert alerte = new Alert(AlertType.INFORMATION);
            alerte.setContentText("Ca ne s'est pas passé comme prévu.");
            alerte.showAndWait();
        }
    }

}
