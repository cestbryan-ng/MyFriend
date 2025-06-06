package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
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
    private Thread threadEmission, threadReception, threadTimer;

    private Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        nom_recepteur.setText(MainPageController.recepteur_audio);
        Platform.runLater(() -> stage = (Stage) root.getScene().getWindow());

        threadReception = new Thread(this::recevoir);
        threadReception.start();

        try {
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
            micro = (TargetDataLine) AudioSystem.getLine(info);
            micro.open(format);
            micro.start();
        } catch (LineUnavailableException | IOException e) {
            encours = false;
            return;
        }

        threadEmission = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (encours && !Thread.currentThread().isInterrupted()) {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                try {
                    Page1Controller.out_audio.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    encours = false;
                    break;
                }
            }
        });
        threadEmission.setDaemon(true);
        threadEmission.start();
    }

    @FXML
    private void raccrocher() {
        encours = false;
        fermerFenetre();
    }

    private void recevoir() {
        threadTimer = new Thread(() -> {
            int minute = 0, seconde = 0;
            while (encours) {
                final int m = minute, s = seconde;
                Platform.runLater(() -> temps.setText(String.format("%02d : %02d", m, s)));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                if (++seconde == 60) {
                    seconde = 0;
                    minute++;
                }
            }
        });
        threadTimer.setDaemon(true);
        threadTimer.start();

        try (SourceDataLine sortie_audio = AudioSystem.getSourceDataLine(format)) {
            sortie_audio.open(format);
            sortie_audio.start();
            byte[] buffer = new byte[1024];

            while (encours) {
                try {
                    int byte_lue = Page1Controller.in_audio.read(buffer);
                    sortie_audio.write(buffer, 0, byte_lue);
                } catch (SocketTimeoutException e) {
                    encours = false;
                }
            }
        } catch (IOException | LineUnavailableException e) {
            encours = false;
        } finally {
            fermerFenetre();
        }
    }

    private void fermerFenetre() {
        try {
            if (micro != null && micro.isActive()) {
                micro.stop();
                micro.close();
            }

            if (Page1Controller.in_audio != null) Page1Controller.in_audio.close();
            if (Page1Controller.out_audio != null) Page1Controller.out_audio.close();
            if (Page1Controller.socket_audio != null) Page1Controller.socket_audio.close();

            if (threadEmission != null) threadEmission.interrupt();
            if (threadReception != null) threadReception.interrupt();
            if (threadTimer != null) threadTimer.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            if (stage == null) stage = (Stage) root.getScene().getWindow();
            if (stage != null) stage.close();
        });
    }
}



