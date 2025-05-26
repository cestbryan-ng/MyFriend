package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;
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

        try {
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
            micro = (TargetDataLine) AudioSystem.getLine(info);
            micro.open(format);
            micro.start();
        } catch (LineUnavailableException | IOException e) {
            encours = false;
            e.printStackTrace();
        }

        new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (encours) {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                try {
                    Page1Controller.out_audio.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    encours = false;
                }
            }
        }).start();
    }

    @FXML
    void raccrocher() throws IOException  {
        encours = false;

        if (micro.isActive()) {
            micro.stop();
            micro.close();
        }

        Page1Controller.in_audio.close();
        Page1Controller.out_audio.close();
        Page1Controller.socket_audio.close();


        Stage stage1 = (Stage) root.getScene().getWindow();
        stage1.close();
    }

    @FXML
    void recevoir() {
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
                } catch (InterruptedException _) {}
            }
        });

        try {
            SourceDataLine sortie_audio = AudioSystem.getSourceDataLine(format);
            sortie_audio.open(format);
            sortie_audio.start();

            byte[] buffer = new byte[4096];
            while (encours) {
                int byte_lue = Page1Controller.in_audio.read(buffer);
                sortie_audio.write(buffer, 0, byte_lue);
                if (!(thread.isAlive())) Platform.runLater(thread::start);
            }
        } catch (IOException | LineUnavailableException e) {
            encours = false;
        }
    }

}
