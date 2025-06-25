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

    // VARIABLES DE SYNCHRONISATION
    private volatile boolean readyToStart = false;
    private volatile boolean otherPersonReady = false;

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

        // PHASE 1: Démarrer les threads de réception (pour recevoir le signal READY)
        threadReceiveVideo = new Thread(this::receiveVideo);
        threadReceiveAudio = new Thread(this::receiveAudio);
        threadReceiveVideo.start();
        threadReceiveAudio.start();

        // PHASE 2: Envoyer notre signal READY et attendre l'autre
        new Thread(this::synchronizeStart).start();
    }

    private void synchronizeStart() {
        try {
            // Attendre un peu pour s'assurer que tout est initialisé
            Thread.sleep(500);

            // Envoyer notre signal READY
            Platform.runLater(() -> message_connexion.setText("Envoi signal de synchronisation..."));

            Page1Controller.out_audio.writeUTF("READY_SIGNAL");
            Page1Controller.out_video.writeInt(-1); // Signal spécial pour "READY"

            readyToStart = true;
            Platform.runLater(() -> message_connexion.setText("En attente de l'autre personne..."));

            // Attendre que l'autre soit prêt (max 10 secondes)
            int attempts = 0;
            while (!otherPersonReady && encours && attempts < 100) {
                Thread.sleep(100);
                attempts++;
            }

            if (!otherPersonReady) {
                Platform.runLater(() -> message_connexion.setText("Timeout - démarrage forcé"));
                Thread.sleep(1000);
            }

            // PHASE 3: Les deux sont prêts, démarrer l'envoi
            Platform.runLater(() -> {
                message_connexion.setText("Synchronisé - démarrage...");
                // Cacher le message après 2 secondes
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Platform.runLater(() -> message_connexion.setVisible(false));
                    } catch (InterruptedException e) {}
                }).start();
            });

            // Démarrer les threads d'envoi ENSEMBLE
            threadSendVideo = new Thread(this::sendVideo);
            threadSendAudio = new Thread(this::sendAudio);

            threadSendAudio.setPriority(Thread.MAX_PRIORITY);

            threadSendVideo.start();
            threadSendAudio.start();

        } catch (IOException | InterruptedException e) {
            encours = false;
            Platform.runLater(this::stopAndClose);
        }
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

                // VÉRIFIER SIGNAL DE SYNCHRONISATION
                if (length == -1) {
                    otherPersonReady = true;
                    Platform.runLater(() -> message_connexion.setText("L'autre personne est prête !"));
                    continue;
                }

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

            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = Page1Controller.in_audio.read(buffer);

                    // VÉRIFIER SIGNAL DE SYNCHRONISATION AUDIO
                    String received = new String(buffer, 0, Math.min(bytesRead, 12));
                    if (received.startsWith("READY_SIGNAL")) {
                        otherPersonReady = true;
                        Platform.runLater(() -> message_connexion.setText("Signal reçu !"));
                        continue;
                    }

                    speaker.write(buffer, 0, bytesRead);

                    // Configurer timeout après première vraie réception audio
                    if (Page1Controller.socket_audio.getSoTimeout() == 0) {
                        Page1Controller.socket_audio.setSoTimeout(2000);
                    }
                } catch (SocketTimeoutException e) {
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