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

public class Page1AppelController implements Initializable {

    @FXML
    private AnchorPane root;

    @FXML
    private Label nom_recepteur;

    @FXML
    private Label temps;

    private static boolean encours = false;
    private static TargetDataLine micro;
    private static final AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static final DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        nom_recepteur.setText(MainPageController.recepteur_audio);

        new Thread(this::recevoir).start();

        try {
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
            micro = (TargetDataLine) AudioSystem.getLine(info);
            micro.open(format);
            micro.start();
        } catch (LineUnavailableException | IOException e) {
            encours = false;
            e.printStackTrace();
            return;
        }

        Thread emissionThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (encours) {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                try {
                    Page1Controller.out_audio.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    encours = false;
                    break;
                }
            }
        });
        emissionThread.setDaemon(true);
        emissionThread.start();
    }

    @FXML
    private void raccrocher() throws IOException {
        encours = false;

        if (micro != null && micro.isActive()) {
            micro.stop();
            micro.close();
        }

        if (Page1Controller.in_audio != null) Page1Controller.in_audio.close();
        if (Page1Controller.out_audio != null) Page1Controller.out_audio.close();
        if (Page1Controller.socket_audio != null) Page1Controller.socket_audio.close();

        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }

    private void recevoir() {
        Thread timerThread = new Thread(() -> {
            int minute = 0, seconde = 0;

            while (encours) {
                final int m = minute;
                final int s = seconde;
                Platform.runLater(() -> temps.setText(String.format("%02d : %02d", m, s)));

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (++seconde == 60) {
                    seconde = 0;
                    minute++;
                }
            }
        });
        timerThread.setDaemon(true);

        try (SourceDataLine sortie_audio = AudioSystem.getSourceDataLine(format)) {
            sortie_audio.open(format);
            sortie_audio.start();

            byte[] buffer = new byte[4096];
            while (encours) {
                int byte_lue = Page1Controller.in_audio.read(buffer);
                sortie_audio.write(buffer, 0, byte_lue);
                if (!(timerThread.isAlive()))  timerThread.start();
            }
        } catch (IOException | LineUnavailableException e) {
            encours = false;
            e.printStackTrace();
        }
    }
}
