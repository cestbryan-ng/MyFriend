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

    // VARIABLES DE SYNCHRONISATION
    private volatile boolean readyToStart = false;
    private volatile boolean otherPersonReady = false;

    // Statistiques simples (optionnel)
    private long audioPacketsSent = 0;
    private long audioPacketsReceived = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        appelEnCours = true;
        nom_recepteur.setText(MainPageController.recepteur_audio);
        Platform.runLater(() -> stage = (Stage) root.getScene().getWindow());

        logger.info("Initialisation appel avec " + MainPageController.recepteur_audio);

        // Démarrer la sonnerie d'appel
        demarrerSonnerie();

        // PHASE 1: Démarrer seulement la réception pour recevoir le signal READY
        threadReception = new Thread(this::recevoir);
        threadReception.setName("AudioReceiver");
        threadReception.start();

        try {
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);

            // Initialiser le microphone
            micro = (TargetDataLine) AudioSystem.getLine(info);
            micro.open(format);
            micro.start();

            logger.info("Audio initialisé - format: " + format);
        } catch (LineUnavailableException | IOException e) {
            logger.log(Level.SEVERE, "Erreur initialisation audio", e);
            encours = false;
            appelEnCours = false;
            arreterSonnerie();
            return;
        }

        // PHASE 2: Gérer la synchronisation
        new Thread(this::synchronizeStart).start();
    }

    private void synchronizeStart() {
        try {
            // Attendre un peu pour s'assurer que tout est initialisé
            Thread.sleep(500);

            // Envoyer notre signal READY
            logger.info("Envoi du signal de synchronisation");
            Page1Controller.out_audio.writeUTF("READY_SIGNAL");

            readyToStart = true;

            // Attendre que l'autre soit prêt (max 10 secondes)
            int attempts = 0;
            while (!otherPersonReady && encours && attempts < 100) {
                Thread.sleep(100);
                attempts++;
            }

            if (!otherPersonReady) {
                logger.warning("Timeout synchronisation - démarrage forcé");
            } else {
                logger.info("Synchronisation réussie - démarrage ensemble");
            }

            // PHASE 3: Les deux sont prêts, démarrer l'émission
            threadEmission = new Thread(this::emettre);
            threadEmission.setName("AudioSender");
            threadEmission.setPriority(Thread.MAX_PRIORITY);
            threadEmission.setDaemon(true);
            threadEmission.start();

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Erreur synchronisation", e);
            encours = false;
            appelEnCours = false;
            arreterSonnerie();
            Platform.runLater(this::fermerFenetre);
        }
    }

    private void emettre() {
        byte[] buffer = new byte[1024];
        logger.info("Démarrage émission audio synchronisée");

        while (encours && !Thread.currentThread().isInterrupted()) {
            int bytesRead = micro.read(buffer, 0, buffer.length);
            try {
                // SIMPLE et DIRECT - pas de synchronized ni flush
                Page1Controller.out_audio.write(buffer, 0, bytesRead);
                audioPacketsSent++;
            } catch (IOException e) {
                if (encours) {
                    logger.log(Level.WARNING, "Erreur émission audio", e);
                }
                encours = false;
                appelEnCours = false;
                arreterSonnerie();
                Platform.runLater(this::fermerFenetre);
                break;
            }
        }
        logger.fine("Arrêt émission audio - paquets envoyés: " + audioPacketsSent);
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

                    // Jouer en boucle tant que l'appel n'est pas décroché
                    while (appelEnCours && !Thread.currentThread().isInterrupted()) {
                        sonnerieClip.setFramePosition(0);
                        sonnerieClip.start();

                        // Attendre que le clip se termine avant de le rejouer
                        while (sonnerieClip.isRunning() && appelEnCours && !Thread.currentThread().isInterrupted()) {
                            Thread.sleep(100);
                        }

                        // Pause entre les sonneries
                        if (appelEnCours && !Thread.currentThread().isInterrupted()) {
                            Thread.sleep(1000);
                        }
                    }
                } else {
                    // Si pas de fichier de sonnerie, générer un bip simple
                    genererSonnerieSimple();
                }
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException e) {
                if (appelEnCours) {
                    logger.log(Level.FINE, "Sonnerie fichier non disponible, utilisation sonnerie simple", e);
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
                byte[] bipData = genererBip(800, 0.5, formatBip);

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
            logger.fine("Arrêt sonnerie simple");
        }
    }

    private byte[] genererBip(int frequence, double duree, AudioFormat format) {
        int numSamples = (int) (duree * format.getSampleRate());
        byte[] buffer = new byte[numSamples * 2];

        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * frequence / format.getSampleRate();
            short sample = (short) (Math.sin(angle) * 32767 * 0.3); // Volume à 30%

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

        logger.fine("Sonnerie arrêtée");
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
            // CRUCIAL: Pas de buffer spécifié pour éviter la latence
            sortie_audio.open(format);
            sortie_audio.start();
            byte[] buffer = new byte[1024];

            logger.info("Démarrage réception audio");

            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int byte_lue = Page1Controller.in_audio.read(buffer);
                    if (byte_lue > 0) {

                        // VÉRIFIER SIGNAL DE SYNCHRONISATION
                        String received = new String(buffer, 0, Math.min(byte_lue, 12));
                        if (received.startsWith("READY_SIGNAL")) {
                            otherPersonReady = true;
                            logger.info("Signal de synchronisation reçu !");
                            continue;
                        }

                        sortie_audio.write(buffer, 0, byte_lue);
                        audioPacketsReceived++;

                        if (!(threadTimer.isAlive())) {
                            // L'autre personne a décroché, arrêter la sonnerie
                            appelEnCours = false;
                            arreterSonnerie();
                            threadTimer.start();

                            // CRUCIAL: Timeout court pour détecter déconnexions rapidement
                            Page1Controller.socket_audio.setSoTimeout(2000);
                            logger.info("Communication établie - timeout configuré");
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout = déconnexion détectée
                    logger.info("Timeout détecté - fin de communication");
                    encours = false;
                    appelEnCours = false;
                    arreterSonnerie();
                    Platform.runLater(this::fermerFenetre);
                } catch (IOException e) {
                    if (encours) {
                        logger.log(Level.WARNING, "Erreur réception audio", e);
                    }
                    break;
                }
            }
        } catch (LineUnavailableException e) {
            logger.log(Level.SEVERE, "Erreur système audio réception", e);
        } finally {
            encours = false;
            appelEnCours = false;
            arreterSonnerie();
            Platform.runLater(this::fermerFenetre);
            logger.info("Arrêt réception audio - paquets reçus: " + audioPacketsReceived);
        }
    }

    private void fermerFenetre() {
        appelEnCours = false;
        arreterSonnerie();

        // Log des statistiques finales
        logger.info(String.format("Fin d'appel - Statistiques: %d paquets envoyés, %d reçus",
                audioPacketsSent, audioPacketsReceived));

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
            if (threadSonnerie != null) threadSonnerie.interrupt();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Erreur fermeture ressources audio", e);
        }

        Platform.runLater(() -> {
            if (stage == null) stage = (Stage) root.getScene().getWindow();
            if (stage != null) stage.close();
        });

        logger.info("Fermeture complète de l'appel");
    }
}