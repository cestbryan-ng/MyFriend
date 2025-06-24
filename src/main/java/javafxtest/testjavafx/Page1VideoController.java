package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

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

    // Configuration vidéo
    private static final int TARGET_FPS = 25;
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int INITIAL_JPEG_QUALITY = 70;
    private static final long FRAME_INTERVAL_MS = 1000 / TARGET_FPS;

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
    private Thread threadSendVideo, threadReceiveVideo, threadSendAudio, threadReceiveAudio, threadMonitoring;
    private static final AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static TargetDataLine micro;
    private Stage stage;

    // Variables pour le monitoring et l'optimisation
    private long lastFrameTime = 0;
    private int currentJpegQuality = INITIAL_JPEG_QUALITY;
    private long framesSent = 0;
    private long framesReceived = 0;
    private long lastStatsUpdate = 0;
    private Timeline connectionMonitor;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        message_connexion.setText("Initialisation...");
        message_connexion.setVisible(true);
        Platform.runLater(() -> stage = (Stage) vbox1.getScene().getWindow());

        // Initialisation en arrière-plan pour éviter de bloquer l'UI
        Platform.runLater(() -> {
            if (!initializeConnections()) {
                stopAndClose();
                return;
            }

            if (!initializeCamera()) {
                message_connexion.setText("Caméra indisponible - Audio seulement");
                // Continuer avec audio seulement
            } else {
                message_connexion.setText("Connexion établie");
                // Cacher le message après 2 secondes
                Timeline hideMessage = new Timeline(new KeyFrame(Duration.seconds(2),
                        e -> message_connexion.setVisible(false)));
                hideMessage.play();
            }

            startThreads();
            startConnectionMonitoring();
        });
    }

    private boolean initializeConnections() {
        try {
            Page1Controller.out_video.writeUTF(MainPageController.adresse_recepteur_video);
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur initialisation connexions", e);
            Platform.runLater(() -> {
                message_connexion.setText("Erreur de connexion réseau");
            });
            return false;
        }
    }

    private boolean initializeCamera() {
        try {
            // Essayer plusieurs indices de caméra
            camera = new VideoCapture(0);

            if (!camera.isOpened()) {
                logger.info("Caméra 0 indisponible, test des autres...");
                for (int i = 1; i < 3; i++) {
                    camera.release();
                    camera = new VideoCapture(i);
                    if (camera.isOpened()) {
                        logger.info("Caméra " + i + " trouvée");
                        break;
                    }
                }
            }

            if (!camera.isOpened()) {
                logger.warning("Aucune caméra disponible");
                return false;
            }

            // Configuration optimale de la caméra
            camera.set(Videoio.CAP_PROP_FRAME_WIDTH, VIDEO_WIDTH);
            camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, VIDEO_HEIGHT);
            camera.set(Videoio.CAP_PROP_FPS, TARGET_FPS);

            // Réduire la latence
            camera.set(Videoio.CAP_PROP_BUFFERSIZE, 1);

            // Test de capture
            Mat testFrame = new Mat();
            if (!camera.read(testFrame)) {
                logger.warning("Impossible de capturer depuis la caméra");
                camera.release();
                return false;
            }

            logger.info("Caméra initialisée avec succès: " +
                    camera.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x" +
                    camera.get(Videoio.CAP_PROP_FRAME_HEIGHT) + " @ " +
                    camera.get(Videoio.CAP_PROP_FPS) + "fps");

            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur initialisation caméra", e);
            if (camera != null) {
                camera.release();
            }
            return false;
        }
    }

    private void startThreads() {
        if (camera != null && camera.isOpened()) {
            threadSendVideo = new Thread(this::sendVideo);
            threadSendVideo.setName("VideoSender");
            threadSendVideo.start();
        }

        threadReceiveVideo = new Thread(this::receiveVideo);
        threadReceiveVideo.setName("VideoReceiver");
        threadReceiveVideo.start();

        threadSendAudio = new Thread(this::sendAudio);
        threadSendAudio.setName("AudioSender");
        threadSendAudio.setPriority(Thread.MAX_PRIORITY);
        threadSendAudio.start();

        threadReceiveAudio = new Thread(this::receiveAudio);
        threadReceiveAudio.setName("AudioReceiver");
        threadReceiveAudio.setPriority(Thread.MAX_PRIORITY);
        threadReceiveAudio.start();
    }

    private void startConnectionMonitoring() {
        connectionMonitor = new Timeline(new KeyFrame(Duration.seconds(3), e -> updateConnectionStats()));
        connectionMonitor.setCycleCount(Timeline.INDEFINITE);
        connectionMonitor.play();
    }

    private void updateConnectionStats() {
        long currentTime = System.currentTimeMillis();
        if (lastStatsUpdate > 0) {
            double secondsElapsed = (currentTime - lastStatsUpdate) / 1000.0;
            double sendFps = framesSent / secondsElapsed;
            double receiveFps = framesReceived / secondsElapsed;

            // Ajuster la qualité en fonction des performances
            adaptVideoQuality(sendFps, receiveFps);

            logger.info(String.format("Stats: Envoi=%.1f fps, Réception=%.1f fps, Qualité=%d",
                    sendFps, receiveFps, currentJpegQuality));
        }

        framesSent = 0;
        framesReceived = 0;
        lastStatsUpdate = currentTime;
    }

    private void adaptVideoQuality(double sendFps, double receiveFps) {
        // Ajuster la qualité selon les performances
        if (sendFps < TARGET_FPS * 0.8 || receiveFps < TARGET_FPS * 0.8) {
            // Performance faible - réduire qualité
            currentJpegQuality = Math.max(40, currentJpegQuality - 10);
        } else if (sendFps > TARGET_FPS * 0.95 && receiveFps > TARGET_FPS * 0.95) {
            // Bonnes performances - augmenter qualité
            currentJpegQuality = Math.min(85, currentJpegQuality + 5);
        }
    }

    private void sendVideo() {
        if (camera == null || !camera.isOpened()) {
            logger.warning("Caméra non disponible pour l'envoi");
            return;
        }

        Mat frame = new Mat();
        MatOfByte buffer = new MatOfByte();
        MatOfInt jpegParams = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, currentJpegQuality);

        logger.info("Démarrage envoi vidéo");

        while (encours && !Thread.currentThread().isInterrupted()) {
            long currentTime = System.currentTimeMillis();

            // Contrôle du framerate
            if (currentTime - lastFrameTime < FRAME_INTERVAL_MS) {
                try {
                    Thread.sleep(5); // Petite pause pour éviter une boucle trop intensive
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            if (camera.read(frame) && !frame.empty()) {
                try {
                    // Redimensionner si nécessaire
                    if (frame.width() != VIDEO_WIDTH || frame.height() != VIDEO_HEIGHT) {
                        Imgproc.resize(frame, frame, new Size(VIDEO_WIDTH, VIDEO_HEIGHT));
                    }

                    // Mise à jour dynamique de la qualité JPEG
                    jpegParams.put(0, 0, currentJpegQuality);
                    Imgcodecs.imencode(".jpg", frame, buffer, jpegParams);
                    byte[] data = buffer.toArray();

                    // Affichage local (preview)
                    Platform.runLater(() -> {
                        try {
                            Image img = new Image(new ByteArrayInputStream(data));
                            imageview1.setImage(img);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Erreur affichage preview", e);
                        }
                    });

                    // Envoi réseau
                    synchronized (Page1Controller.out_video) {
                        Page1Controller.out_video.writeInt(data.length);
                        Page1Controller.out_video.write(data);
                        Page1Controller.out_video.flush();
                    }

                    framesSent++;
                    lastFrameTime = currentTime;

                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Erreur envoi frame vidéo", e);
                    encours = false;
                    break;
                }
            } else {
                // Problème de lecture caméra
                logger.warning("Impossible de lire depuis la caméra");
                try {
                    Thread.sleep(100); // Pause avant retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (camera != null) {
            camera.release();
        }
        logger.info("Arrêt envoi vidéo");
    }

    private void receiveVideo() {
        logger.info("Démarrage réception vidéo");

        while (encours && !Thread.currentThread().isInterrupted()) {
            try {
                // Lecture avec timeout pour éviter les blocages
                int length = Page1Controller.in_video.readInt();

                if (length <= 0 || length > 1024 * 1024) { // Max 1MB par frame
                    logger.warning("Taille de frame invalide: " + length);
                    continue;
                }

                byte[] data = new byte[length];
                Page1Controller.in_video.readFully(data);

                Platform.runLater(() -> {
                    try {
                        Image img = new Image(new ByteArrayInputStream(data));
                        imageview2.setImage(img);
                        framesReceived++;
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Erreur affichage frame reçue", e);
                    }
                });

            } catch (IOException e) {
                if (encours) {
                    logger.log(Level.SEVERE, "Erreur réception vidéo", e);
                }
                encours = false;
                break;
            }
        }

        logger.info("Arrêt réception vidéo");
    }

    private void sendAudio() {
        try {
            if (!AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, format))) {
                logger.warning("Format audio non supporté");
                return;
            }

            micro = AudioSystem.getTargetDataLine(format);
            micro.open(format, 4096); // Buffer plus petit pour réduire latence
            micro.start();

            logger.info("Démarrage envoi audio");

            byte[] buffer = new byte[1024];
            while (encours && !Thread.currentThread().isInterrupted()) {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    synchronized (Page1Controller.out_audio) {
                        Page1Controller.out_audio.write(buffer, 0, bytesRead);
                        Page1Controller.out_audio.flush();
                    }
                }
            }
        } catch (LineUnavailableException | IOException e) {
            logger.log(Level.SEVERE, "Erreur envoi audio", e);
            encours = false;
        } finally {
            if (micro != null) {
                micro.stop();
                micro.close();
            }
            logger.info("Arrêt envoi audio");
        }
    }

    private void receiveAudio() {
        try (SourceDataLine speaker = AudioSystem.getSourceDataLine(format)) {
            speaker.open(format, 4096);
            speaker.start();

            logger.info("Démarrage réception audio");

            byte[] buffer = new byte[1024];
            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = Page1Controller.in_audio.read(buffer);
                    if (bytesRead > 0) {
                        speaker.write(buffer, 0, bytesRead);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout normal, continuer
                } catch (IOException e) {
                    if (encours) {
                        logger.log(Level.WARNING, "Erreur réception audio", e);
                    }
                    break;
                }
            }
        } catch (LineUnavailableException e) {
            logger.log(Level.SEVERE, "Erreur réception audio", e);
            encours = false;
        } finally {
            logger.info("Arrêt réception audio");
        }
    }

    @FXML
    private void racrocher() {
        logger.info("Raccrochage demandé par l'utilisateur");
        encours = false;
        stopAndClose();
    }

    private void stopAndClose() {
        encours = false;

        // Arrêter le monitoring
        if (connectionMonitor != null) {
            connectionMonitor.stop();
        }

        try {
            // Fermeture audio
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
            logger.log(Level.WARNING, "Erreur lors de la fermeture", e);
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