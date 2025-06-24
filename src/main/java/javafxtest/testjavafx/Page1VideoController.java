package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.opencv.core.Core;
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
import java.util.logging.Logger;
import java.util.logging.Level;

public class Page1VideoController implements Initializable {
    private static final Logger logger = Logger.getLogger(Page1VideoController.class.getName());

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
        message_connexion.setVisible(false);
        Platform.runLater(() -> stage = (Stage) vbox1.getScene().getWindow());

        logger.info("Initialisation appel vidéo");

        try {
            Page1Controller.out_video.writeUTF(MainPageController.adresse_recepteur_video);
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur envoi adresses destinataires", e);
            stopAndClose();
            return;
        }

        // Initialiser la caméra SIMPLEMENT
        camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            logger.warning("Caméra non disponible");
            stopAndClose();
            return;
        }

        logger.info("Caméra initialisée");

        // Démarrer les threads SIMPLEMENT
        threadSendVideo = new Thread(this::sendVideo);
        threadReceiveVideo = new Thread(this::receiveVideo);
        threadSendAudio = new Thread(this::sendAudio);
        threadReceiveAudio = new Thread(this::receiveAudio);

        // Priorité élevée pour l'audio (comme dans l'original)
        threadReceiveAudio.setPriority(Thread.MAX_PRIORITY);
        threadSendAudio.setPriority(Thread.MAX_PRIORITY);

        threadSendVideo.start();
        threadReceiveVideo.start();
        threadSendAudio.start();
        threadReceiveAudio.start();
    }

    private void sendVideo() {
        Mat frame = new Mat();
        MatOfByte buffer = new MatOfByte();

        logger.fine("Démarrage envoi vidéo");

        while (encours && !Thread.currentThread().isInterrupted()) {
            if (camera.read(frame)) {
                Imgcodecs.imencode(".jpg", frame, buffer);
                byte[] data = buffer.toArray();

                Platform.runLater(() -> {
                    Image img = new Image(new ByteArrayInputStream(data));
                    imageview1.setImage(img);
                });

                try {
                    // SIMPLE et DIRECT - comme l'original audio
                    Page1Controller.out_video.writeInt(data.length);
                    Page1Controller.out_video.write(data);
                } catch (IOException e) {
                    if (encours) {
                        logger.log(Level.WARNING, "Erreur envoi frame vidéo", e);
                    }
                    encours = false;
                    Platform.runLater(this::stopAndClose);
                    break;
                }
            }
        }
        if (camera != null) {
            camera.release();
        }
        logger.fine("Arrêt envoi vidéo");
    }

    private void receiveVideo() {
        logger.fine("Démarrage réception vidéo");

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
                if (encours) {
                    logger.log(Level.WARNING, "Erreur réception vidéo", e);
                }
                encours = false;
                Platform.runLater(this::stopAndClose);
                break;
            }
        }
        logger.fine("Arrêt réception vidéo");
    }

    private void sendAudio() {
        try {
            micro = AudioSystem.getTargetDataLine(format);
            // CRUCIAL: Pas de buffer spécifié pour éviter la latence
            micro.open(format);
            micro.start();

            logger.fine("Démarrage envoi audio");

            byte[] buffer = new byte[1024];
            while (encours && !Thread.currentThread().isInterrupted()) {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // SIMPLE et DIRECT - pas de synchronized ni flush
                    Page1Controller.out_audio.write(buffer, 0, bytesRead);
                }
            }
        } catch (LineUnavailableException | IOException e) {
            if (encours) {
                logger.log(Level.WARNING, "Erreur envoi audio", e);
            }
            encours = false;
            Platform.runLater(this::stopAndClose);
        } finally {
            if (micro != null) {
                micro.stop();
                micro.close();
            }
            logger.fine("Arrêt envoi audio");
        }
    }

    private void receiveAudio() {
        try (SourceDataLine speaker = AudioSystem.getSourceDataLine(format)) {
            // CRUCIAL: Pas de buffer spécifié pour éviter la latence
            speaker.open(format);
            speaker.start();

            logger.fine("Démarrage réception audio");

            byte[] buffer = new byte[1024];
            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = Page1Controller.in_audio.read(buffer);
                    if (bytesRead > 0) {
                        speaker.write(buffer, 0, bytesRead);
                    }
                } catch (SocketTimeoutException e) {
                    // CRUCIAL: Timeout = déconnexion détectée
                    logger.info("Timeout audio détecté - fin de communication vidéo");
                    encours = false;
                    Platform.runLater(this::stopAndClose);
                } catch (IOException e) {
                    if (encours) {
                        logger.log(Level.WARNING, "Erreur réception audio", e);
                    }
                    break;
                }
            }
        } catch (LineUnavailableException e) {
            logger.log(Level.SEVERE, "Erreur système audio", e);
        } finally {
            encours = false;
            Platform.runLater(this::stopAndClose);
            logger.fine("Arrêt réception audio");
        }
    }

    @FXML
    private void racrocher() {
        logger.info("Raccrochage vidéo demandé par l'utilisateur");
        encours = false;
        stopAndClose();
    }

    private void stopAndClose() {
        encours = false;

        try {
            // Fermeture micro
            if (micro != null && micro.isActive()) {
                micro.stop();
                micro.close();
            }

            // Fermeture caméra
            if (camera != null && camera.isOpened()) {
                camera.release();
            }

            // Fermeture connexions réseau
            if (Page1Controller.in_video != null) Page1Controller.in_video.close();
            if (Page1Controller.out_video != null) Page1Controller.out_video.close();
            if (Page1Controller.socket_video != null) Page1Controller.socket_video.close();

            if (Page1Controller.in_audio != null) Page1Controller.in_audio.close();
            if (Page1Controller.out_audio != null) Page1Controller.out_audio.close();
            if (Page1Controller.socket_audio != null) Page1Controller.socket_audio.close();

            // Arrêt des threads
            if (threadSendVideo != null) threadSendVideo.interrupt();
            if (threadReceiveVideo != null) threadReceiveVideo.interrupt();
            if (threadSendAudio != null) threadSendAudio.interrupt();
            if (threadReceiveAudio != null) threadReceiveAudio.interrupt();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Erreur fermeture ressources vidéo", e);
        }

        Platform.runLater(() -> {
            if (stage == null && vbox1.getScene() != null)
                stage = (Stage) vbox1.getScene().getWindow();
            if (stage != null)
                stage.close();
        });

        logger.info("Fermeture complète de la session vidéo");
    }
}