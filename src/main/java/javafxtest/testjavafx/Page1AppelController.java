package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;

public class Page1AppelController implements Initializable  {

    @FXML
    private AnchorPane root;

    @FXML
    private Label nom_recepteur;

    @FXML
    private Label temps;


    static boolean encours = false;
    private static TargetDataLine micro;
    private static AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

    public void initialize(URL location, ResourceBundle resources) {
        new Thread(this::recevoir).start();
        encours = true;
        nom_recepteur.setText(MainPageController.recepteur_audio);

        new Thread(() -> {
            try {
                Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
                while (encours) {
                    Page1Controller.out_audio.writeUTF("a");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


//
//        String adresse_recepteur = "";
//
//        try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
//            ResultSet resultSet1 = stmt.executeQuery("select adresse_ip from connected_user\n" +
//                    "where user_id in \n" +
//                    "(select user_id from user where username = \"" + Page1Controller.recepteur + "\");");
//            while (resultSet1.next()) {
//                adresse_recepteur = resultSet1.getString(1);
//                adresse_recepteur = "/" + adresse_recepteur.split("/")[1];
//            }
//            resultSet1.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//        try {
//            Page1Controller.out_audio.writeUTF(adresse_recepteur);
//            Page1Controller.out_audio.writeUTF("appel");
//            micro = (TargetDataLine) AudioSystem.getLine(info);
//            micro.open(format);
//            micro.start();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        catch (LineUnavailableException e) {
//            throw new RuntimeException(e);
//        }
//
//        encours = true;
//
//        Thread thread = new Thread(() -> {
//            byte[] buffer_audio = new byte[4096];
//            while (encours) {
//                int bytesRead = micro.read(buffer_audio, 0, buffer_audio.length);
//                try {
//                    Page1Controller.out_audio.write(buffer_audio, 0, bytesRead);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        thread.start();

    }

    @FXML
    void raccrocher() throws IOException {
        encours = false;
        Stage stage1 = (Stage) root.getScene().getWindow();
        stage1.close();

//        if (micro.isActive()) {
//            micro.stop();
//            micro.close();
//        }

        Page1Controller.in_audio.close();
        Page1Controller.out_audio.close();
        Page1Controller.socket_audio.close();
    }

    @FXML
    void recevoir() {
        String message;

        Thread thread = new Thread(() -> {
            Integer minute = 0, seconde = 0;

            while (encours) {
                if (seconde == 60) {
                    seconde = 1;
                    minute++;
                    Integer finalMinute = minute, finalSeconde = seconde;
                    Platform.runLater(() -> {
                        temps.setText(finalMinute + " : " + finalSeconde);
                    });
                } else {
                    Integer finalMinute1 = minute, finalSeconde1 = seconde;
                    Platform.runLater(() -> {
                        temps.setText(finalMinute1 + " : " + finalSeconde1);
                    });
                    seconde++;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        while (true) {
            try {
                message = Page1Controller.in_audio.readUTF();
            } catch (IOException e) {
                try {
                    raccrocher();
                } catch (IOException err) {}
                break;
            }
            if (!(thread.isAlive())) Platform.runLater(thread::start);
        }

//        Platform.runLater(() -> {
//            nom_recepteur.setText(Page1Controller.recepteur);
//        });
////
//        try {
//            SourceDataLine sortie_audio = AudioSystem.getSourceDataLine(format);
//            sortie_audio.open(format);
//            sortie_audio.start();
//
//            int byte_lue;
//            byte[] buffer = new byte[4096];
//            while ((byte_lue = Page1Controller.in_audio.read(buffer)) != -1) {
//                sortie_audio.write(buffer, 0, byte_lue);
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (LineUnavailableException e) {
//            throw new RuntimeException(e);
//        }
    }

}
