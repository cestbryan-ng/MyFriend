package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ResourceBundle;

public class Page1VideoController implements Initializable {

    @FXML
    private VBox vbox1;

    @FXML
    private ImageView imageview1;

    @FXML
    private ImageView imageview2;

    @FXML
    private Label message_connexion;

    private static boolean encours = false;
    private VideoCapture camera;
    private Thread threadSendVideo, threadReceiveVideo, threadSendAudio, threadReceiveAudio;
    private static final AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static TargetDataLine micro;
    private Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        message_connexion.setText("Connexion en cours...");
        message_connexion.setVisible(true);
        Platform.runLater(() -> stage = (Stage) vbox1.getScene().getWindow());

        try {
            Page1Controller.out_video.writeUTF(MainPageController.adresse_recepteur_video);
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
        } catch (IOException e) {
            encours = false;
            stopAndClose();
            return;
        }

        camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            stopAndClose();
            return;
        }

        // Initialiser l'audio
        try {
            micro = AudioSystem.getTargetDataLine(format);
            micro.open(format);
            micro.start();
        } catch (LineUnavailableException e) {
            encours = false;
            stopAndClose();
            return;
        }

        // Démarrer immédiatement les threads de RÉCEPTION
        threadReceiveVideo = new Thread(this::receiveVideo);
        threadReceiveAudio = new Thread(this::receiveAudio);
        threadReceiveVideo.setName("VideoReceiver");
        threadReceiveAudio.setName("AudioReceiver");
        threadReceiveAudio.setPriority(Thread.MAX_PRIORITY);

        threadReceiveVideo.start();
        threadReceiveAudio.start();

        // DÉLAI DE SYNCHRONISATION SIMPLE
        new Thread(() -> {
            try {
                Platform.runLater(() -> message_connexion.setText("Synchronisation..."));
                Thread.sleep(2000); // Délai fixe pour que les 2 machines soient prêtes

                if (encours) {
                    Platform.runLater(() -> {
                        message_connexion.setText("Démarrage...");
                        // Cacher le message après 1 seconde
                        new Thread(() -> {
                            try {
                                Thread.sleep(1000);
                                Platform.runLater(() -> message_connexion.setVisible(false));
                            } catch (InterruptedException e) {}
                        }).start();
                    });

                    // Démarrer les threads d'ENVOI ENSEMBLE
                    threadSendVideo = new Thread(this::sendVideo);
                    threadSendAudio = new Thread(this::sendAudio);

                    threadSendVideo.setName("VideoSender");
                    threadSendAudio.setName("AudioSender");
                    threadSendAudio.setPriority(Thread.MAX_PRIORITY);

                    threadSendVideo.start();
                    threadSendAudio.start();
                }

            } catch (InterruptedException e) {
                // Synchronisation interrompue
            }
        }).start();
    }

    private void sendVideo() {
        Mat frame = new Mat();
        MatOfByte buffer = new MatOfByte();

        while (encours && !Thread.currentThread().isInterrupted()) {
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
                    encours = false;
                    Platform.runLater(this::stopAndClose);
                    break;
                }
            }
        }
        if (camera != null) {
            camera.release();
        }
    }

    private void receiveVideo() {
        while (encours && !Thread.currentThread().isInterrupted()) {
            try {
                int length = Page1Controller.in_video.readInt();
                byte[] data = new byte[length];
                Page1Controller.in_video.readFully(data);

                Platform.runLater(() -> {
                    Image img = new Image(new ByteArrayInputStream(data));
                    imageview2.setImage(img);
                });
            } catch (IOException e) {
                encours = false;
                Platform.runLater(this::stopAndClose);
                break;
            }
        }
    }

    private void sendAudio() {
        byte[] buffer = new byte[1024];
        while (encours && !Thread.currentThread().isInterrupted()) {
            int bytesRead = micro.read(buffer, 0, buffer.length);
            try {
                Page1Controller.out_audio.write(buffer, 0, bytesRead);
            } catch (IOException e) {
                encours = false;
                Platform.runLater(this::stopAndClose);
                break;
            }
        }
    }

    private void receiveAudio() {
        try (SourceDataLine speaker = AudioSystem.getSourceDataLine(format)) {
            speaker.open(format);
            speaker.start();
            byte[] buffer = new byte[1024];
            boolean timeoutConfigured = false;

            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = Page1Controller.in_audio.read(buffer);
                    if (bytesRead > 0) {
                        speaker.write(buffer, 0, bytesRead);

                        // CONFIGURER TIMEOUT SEULEMENT APRÈS PREMIÈRE RÉCEPTION AUDIO
                        if (!timeoutConfigured) {
                            Page1Controller.socket_audio.setSoTimeout(2000);
                            timeoutConfigured = true;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout = déconnexion détectée
                    encours = false;
                    Platform.runLater(this::stopAndClose);
                    break;
                } catch (IOException e) {
                    if (encours) {
                        encours = false;
                        Platform.runLater(this::stopAndClose);
                    }
                    break;
                }
            }
        } catch (LineUnavailableException e) {
            encours = false;
            Platform.runLater(this::stopAndClose);
        }
    }

    @FXML
    private void racrocher() {
        encours = false;
        stopAndClose();
    }

    private void stopAndClose() {
        encours = false;

        try {
            if (micro != null && micro.isActive()) {
                micro.stop();
                micro.close();
            }

            if (camera != null && camera.isOpened()) camera.release();

            if (Page1Controller.in_video != null) Page1Controller.in_video.close();
            if (Page1Controller.out_video != null) Page1Controller.out_video.close();
            if (Page1Controller.socket_video != null) Page1Controller.socket_video.close();

            if (Page1Controller.in_audio != null) Page1Controller.in_audio.close();
            if (Page1Controller.out_audio != null) Page1Controller.out_audio.close();
            if (Page1Controller.socket_audio != null) Page1Controller.socket_audio.close();

            if (threadSendVideo != null) threadSendVideo.interrupt();
            if (threadReceiveVideo != null) threadReceiveVideo.interrupt();
            if (threadSendAudio != null) threadSendAudio.interrupt();
            if (threadReceiveAudio != null) threadReceiveAudio.interrupt();

        } catch (IOException e) {
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            if (stage == null && vbox1.getScene() != null)
                stage = (Stage) vbox1.getScene().getWindow();
            if (stage != null)
                stage.close();
        });
    }
}