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
import java.util.logging.Logger;
import java.util.logging.Level;

public class Page1AppelController implements Initializable {
    private static final Logger logger = Logger.getLogger(Page1AppelController.class.getName());

    @FXML
    private AnchorPane root;

    @FXML
    private Label nom_recepteur;

    @FXML
    private Label temps;

    private static boolean encours = false;
    private static boolean appelEnCours = true;
    private static TargetDataLine micro;
    private static final AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static final DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    private Thread threadEmission, threadReception, threadTimer, threadSonnerie;

    private Stage stage;
    private Clip sonnerieClip;

    // Variables pour le monitoring audio
    private long audioPacketsSent = 0;
    private long audioPacketsReceived = 0;
    private long lastStatsTime = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        appelEnCours = true;
        nom_recepteur.setText(MainPageController.recepteur_audio);
        Platform.runLater(() -> stage = (Stage) root.getScene().getWindow());

        logger.info("Initialisation appel avec " + MainPageController.recepteur_audio);

        // Démarrer la sonnerie d'appel
        demarrerSonnerie();

        // Initialiser les composants audio
        if (!initializeAudio()) {
            logger.severe("Échec initialisation audio");
            encours = false;
            appelEnCours = false;
            arreterSonnerie();
            fermerFenetre();
            return;
        }

        // Démarrer les threads
        startThreads();
    }

    private boolean initializeAudio() {
        try {
            // Vérifier support du format audio
            if (!AudioSystem.isLineSupported(info)) {
                logger.warning("Format audio non supporté");
                return false;
            }

            // Envoyer l'adresse de destination
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);

            // Initialiser le microphone
            micro = (TargetDataLine) AudioSystem.getLine(info);
            micro.open(format, 4096); // Buffer réduit pour moins de latence
            micro.start();

            logger.info("Audio initialisé avec succès");
            return true;

        } catch (LineUnavailableException | IOException e) {
            logger.log(Level.SEVERE, "Erreur initialisation audio", e);
            return false;
        }
    }

    private void startThreads() {
        threadReception = new Thread(this::recevoir);
        threadReception.setName("AudioReceiver");
        threadReception.setPriority(Thread.MAX_PRIORITY);
        threadReception.start();

        threadEmission = new Thread(this::emettre);
        threadEmission.setName("AudioSender");
        threadEmission.setPriority(Thread.MAX_PRIORITY);
        threadEmission.setDaemon(true);
        threadEmission.start();

        logger.info("Threads audio démarrés");
    }

    private void emettre() {
        byte[] buffer = new byte[1024];
        logger.info("Démarrage émission audio");

        while (encours && !Thread.currentThread().isInterrupted()) {
            try {
                int bytesRead = micro.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    synchronized (Page1Controller.out_audio) {
                        Page1Controller.out_audio.write(buffer, 0, bytesRead);
                        Page1Controller.out_audio.flush();
                    }
                    audioPacketsSent++;
                }
            } catch (IOException e) {
                if (encours) {
                    logger.log(Level.SEVERE, "Erreur émission audio", e);
                }
                encours = false;
                appelEnCours = false;
                arreterSonnerie();
                Platform.runLater(this::fermerFenetre);
                break;
            }
        }

        logger.info("Arrêt émission audio");
    }

    private void demarrerSonnerie() {
        threadSonnerie = new Thread(() -> {
            try {
                // Charger le fichier de sonnerie
                URL sonnerieUrl = getClass().getResource("telephone-ring-0.wav");
                if (sonnerieUrl != null) {
                    AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(sonnerieUrl);
                    sonnerieClip = AudioSystem.getClip();
                    sonnerieClip.open(audioInputStream);

                    // Jouer en boucle
                    while (appelEnCours && !Thread.currentThread().isInterrupted()) {
                        sonnerieClip.setFramePosition(0);
                        sonnerieClip.start();

                        while (sonnerieClip.isRunning() && appelEnCours && !Thread.currentThread().isInterrupted()) {
                            Thread.sleep(100);
                        }

                        if (appelEnCours && !Thread.currentThread().isInterrupted()) {
                            Thread.sleep(1000);
                        }
                    }
                } else {
                    // Sonnerie générée si pas de fichier
                    genererSonnerieSimple();
                }
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException e) {
                if (appelEnCours) {
                    logger.log(Level.WARNING, "Erreur sonnerie, utilisation sonnerie simple", e);
                    genererSonnerieSimple();
                }
            }
        });
        threadSonnerie.setName("RingTone");
        threadSonnerie.setDaemon(true);
        threadSonnerie.start();
    }

    private void genererSonnerieSimple() {
        try {
            AudioFormat formatBip = new AudioFormat(44100, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(formatBip);
            line.open(formatBip);
            line.start();

            while (appelEnCours && !Thread.currentThread().isInterrupted()) {
                // Générer pattern de sonnerie : 2 bips + pause
                byte[] bipData = genererBip(950, 0.4, formatBip);

                // Premier bip
                line.write(bipData, 0, bipData.length);
                if (!appelEnCours) break;
                Thread.sleep(150);

                // Deuxième bip
                line.write(bipData, 0, bipData.length);
                if (!appelEnCours) break;
                Thread.sleep(300);

                // Pause longue
                if (appelEnCours && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(500);
                }
            }

            line.drain();
            line.close();
        } catch (LineUnavailableException | InterruptedException e) {
            logger.log(Level.FINE, "Arrêt sonnerie simple", e);
        }
    }

    private byte[] genererBip(int frequence, double duree, AudioFormat format) {
        int numSamples = (int) (duree * format.getSampleRate());
        byte[] buffer = new byte[numSamples * 2];

        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * frequence / format.getSampleRate();
            short sample = (short) (Math.sin(angle) * 32767 * 0.4); // Volume à 40%

            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return buffer;
    }

    private void arreterSonnerie() {
        appelEnCours = false;

        if (threadSonnerie != null) {
            threadSonnerie.interrupt();
        }

        if (sonnerieClip != null && sonnerieClip.isRunning()) {
            sonnerieClip.stop();
            sonnerieClip.close();
        }

        logger.info("Sonnerie arrêtée");
    }

    @FXML
    private void raccrocher() {
        logger.info("Raccrochage demandé par l'utilisateur");
        encours = false;
        appelEnCours = false;
        arreterSonnerie();
        fermerFenetre();
    }

    private void recevoir() {
        threadTimer = new Thread(() -> {
            int minute = 0, seconde = 0;
            while (encours && !Thread.currentThread().isInterrupted()) {
                final int m = minute, s = seconde;
                Platform.runLater(() -> temps.setText(String.format("%02d : %02d", m, s)));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (++seconde == 60) {
                    seconde = 0;
                    minute++;
                }
            }
        });
        threadTimer.setName("CallTimer");
        threadTimer.setDaemon(true);

        try (SourceDataLine sortie_audio = AudioSystem.getSourceDataLine(format)) {
            sortie_audio.open(format, 4096);
            sortie_audio.start();
            byte[] buffer = new byte[1024];

            logger.info("Démarrage réception audio");

            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = Page1Controller.in_audio.read(buffer);
                    if (bytesRead > 0) {
                        sortie_audio.write(buffer, 0, bytesRead);
                        audioPacketsReceived++;

                        if (!threadTimer.isAlive()) {
                            // L'autre personne a décroché
                            appelEnCours = false;
                            arreterSonnerie();
                            threadTimer.start();

                            // Configurer timeout pour détecter déconnexion
                            Page1Controller.socket_audio.setSoTimeout(5000);

                            logger.info("Communication établie");
                        }
                    }
                } catch (SocketTimeoutException e) {
                    if (threadTimer.isAlive()) {
                        // Timeout pendant la communication = déconnexion
                        logger.info("Timeout - fin de communication");
                        encours = false;
                        appelEnCours = false;
                        arreterSonnerie();
                        Platform.runLater(this::fermerFenetre);
                    }
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
            appelEnCours = false;
            arreterSonnerie();
            Platform.runLater(this::fermerFenetre);
            logger.info("Arrêt réception audio");
        }
    }

    private void fermerFenetre() {
        appelEnCours = false;
        arreterSonnerie();

        // Log des statistiques finales
        logger.info(String.format("Statistiques appel: %d paquets envoyés, %d reçus",
                audioPacketsSent, audioPacketsReceived));

        try {
            // Fermeture audio
            if (micro != null && micro.isActive()) {
                micro.stop();
                micro.close();
            }

            // Fermeture connexions
            if (Page1Controller.in_audio != null) Page1Controller.in_audio.close();
            if (Page1Controller.out_audio != null) Page1Controller.out_audio.close();
            if (Page1Controller.socket_audio != null) Page1Controller.socket_audio.close();

            // Arrêt threads
            if (threadEmission != null) threadEmission.interrupt();
            if (threadReception != null) threadReception.interrupt();
            if (threadTimer != null) threadTimer.interrupt();
            if (threadSonnerie != null) threadSonnerie.interrupt();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Erreur fermeture ressources", e);
        }

        Platform.runLater(() -> {
            if (stage == null) stage = (Stage) root.getScene().getWindow();
            if (stage != null) stage.close();
        });

        logger.info("Fermeture complète de l'appel");
    }
}