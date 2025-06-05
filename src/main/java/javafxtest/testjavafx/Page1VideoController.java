package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class Page1VideoController implements Initializable {

    @FXML
    private HBox hbox1;

    @FXML
    private ImageView imageview1;

    @FXML
    private ImageView imageview2;

    @FXML
    private Label message_connexion;

    private static boolean encours = false;
    private VideoCapture camera;

    private static final AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static TargetDataLine micro;
    private Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        message_connexion.setVisible(false);

        Platform.runLater(() -> stage = (Stage) hbox1.getScene().getWindow());

        try {
            Page1Controller.out_video.writeUTF(MainPageController.adresse_recepteur_video);
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
        } catch (IOException e) {
            e.printStackTrace();
            stopAndClose();
            return;
        }

        camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.err.println("Impossible d'ouvrir la caméra");
            stopAndClose();
            return;
        }

        new Thread(this::sendVideo).start();
        new Thread(this::receiveVideo).start();
        new Thread(this::sendAudio).start();
        new Thread(this::receiveAudio).start();
    }

    private void sendVideo() {
        Mat frame = new Mat();
        MatOfByte buffer = new MatOfByte();

        while (encours) {
            if (camera.read(frame)) {
                Imgcodecs.imencode(".jpg", frame, buffer);
                byte[] data = buffer.toArray();

                Platform.runLater(() -> {
                    Image img = new Image(new ByteArrayInputStream(data));
                    imageview1.setImage(img);
                });

                try {
                    Page1Controller.out_video.writeInt(data.length);
                    Page1Controller.out_video.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                    encours = false;
                }
            }
        }
        camera.release();
        stopAndClose();
    }

    private void receiveVideo() {
        while (encours) {
            try {
                int length = Page1Controller.in_video.readInt();
                byte[] data = new byte[length];
                Page1Controller.in_video.readFully(data);

                Platform.runLater(() -> {
                    Image img = new Image(new ByteArrayInputStream(data));
                    imageview2.setImage(img);
                });
            } catch (IOException e) {
                e.printStackTrace();
                encours = false;
            }
        }
        stopAndClose();
    }

    private void sendAudio() {
        try {
            micro = AudioSystem.getTargetDataLine(format);
            micro.open(format);
            micro.start();

            byte[] buffer = new byte[4096];
            while (encours) {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                Page1Controller.out_audio.write(buffer, 0, bytesRead);
            }
        } catch (LineUnavailableException | IOException e) {
            e.printStackTrace();
            encours = false;
        }
        stopAndClose();
    }

    private void receiveAudio() {
        try (SourceDataLine speaker = AudioSystem.getSourceDataLine(format)) {
            speaker.open(format);
            speaker.start();

            byte[] buffer = new byte[4096];
            while (encours) {
                int bytesRead = Page1Controller.in_audio.read(buffer);
                speaker.write(buffer, 0, bytesRead);
            }
        } catch (IOException | LineUnavailableException e) {
            e.printStackTrace();
            encours = false;
        }
        stopAndClose();
    }

    @FXML
    private void racrocher() throws IOException {
        encours = false;
        stopAndClose();
    }

    private void stopAndClose() {
        // Empêche l’exécution multiple
        if (!encours) {
            encours = false;

            try {
                if (micro != null && micro.isActive()) {
                    micro.stop();
                    micro.close();
                }

                if (Page1Controller.in_video != null) Page1Controller.in_video.close();
                if (Page1Controller.out_video != null) Page1Controller.out_video.close();
                if (Page1Controller.socket_video != null) Page1Controller.socket_video.close();

                if (Page1Controller.in_audio != null) Page1Controller.in_audio.close();
                if (Page1Controller.out_audio != null) Page1Controller.out_audio.close();
                if (Page1Controller.socket_audio != null) Page1Controller.socket_audio.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Platform.runLater(() -> {
                if (stage == null && hbox1.getScene() != null) {
                    stage = (Stage) hbox1.getScene().getWindow();
                }
                if (stage != null) {
                    stage.close();
                }
            });
        }
    }
}
