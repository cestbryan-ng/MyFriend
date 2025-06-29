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

        threadReception = new Thread(this::recevoir);
        threadReception.setName("AudioReceiver");
        threadReception.start();

        try {
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
            micro = (TargetDataLine) AudioSystem.getLine(info);

            // CRUCIAL: Pas de buffer spécifié pour éviter la latence
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

        threadEmission = new Thread(() -> {
            byte[] buffer = new byte[1024];
            logger.fine("Démarrage émission audio");

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
        });
        threadEmission.setName("AudioSender");
        threadEmission.setDaemon(true);
        threadEmission.start();
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
                // Vérifier appelEnCours plus fréquemment
                if (!appelEnCours) break;

                // Générer un bip de 800Hz pendant 0.5 seconde
                byte[] bipData = genererBip(800, 0.3, formatBip); // Durée réduite
                line.write(bipData, 0, bipData.length);

                // Vérifications fréquentes pendant les pauses
                for (int i = 0; i < 10 && appelEnCours && !Thread.currentThread().isInterrupted(); i++) {
                    Thread.sleep(30); // 10 x 30ms = 300ms total
                }

                if (!appelEnCours) break;

                // Deuxième bip
                line.write(bipData, 0, bipData.length);

                // Pause plus longue avec vérifications fréquentes
                for (int i = 0; i < 15 && appelEnCours && !Thread.currentThread().isInterrupted(); i++) {
                    Thread.sleep(35); // 15 x 35ms = 525ms total
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
        byte[] buffer = new byte[numSamples * 2]; // 16 bits = 2 bytes

        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * frequence / format.getSampleRate();
            short sample = (short) (Math.sin(angle) * 32767 * 0.3); // Volume à 30%

            // Conversion en little-endian
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return buffer;
    }

    private void arreterSonnerie() {
        appelEnCours = false;

        if (threadSonnerie != null && threadSonnerie.isAlive()) {
            threadSonnerie.interrupt();
            try {
                threadSonnerie.join(500); // Attendre max 500ms que le thread s'arrête
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (sonnerieClip != null) {
            if (sonnerieClip.isRunning()) {
                sonnerieClip.stop();
            }
            if (sonnerieClip.isOpen()) {
                sonnerieClip.close();
            }
            sonnerieClip = null;
        }

        logger.fine("Sonnerie arrêtée définitivement");
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
            sortie_audio.open(format);
            sortie_audio.start();
            byte[] buffer = new byte[1024];

            logger.info("Démarrage réception audio");
            boolean premierePaquetRecu = false; // NOUVEAU FLAG

            while (encours && !Thread.currentThread().isInterrupted()) {
                try {
                    int byte_lue = Page1Controller.in_audio.read(buffer);
                    if (byte_lue > 0) {
                        // ARRÊTER LA SONNERIE DÈS LE PREMIER PAQUET REÇU
                        if (!premierePaquetRecu) {
                            premierePaquetRecu = true;
                            appelEnCours = false;  // Arrêter la sonnerie IMMÉDIATEMENT
                            arreterSonnerie();

                            // Démarrer le timer seulement maintenant
                            if (!threadTimer.isAlive()) {
                                threadTimer.start();
                            }

                            // Configurer timeout après première réception
                            Page1Controller.socket_audio.setSoTimeout(500);
                            logger.info("Communication établie - sonnerie arrêtée");
                        }

                        // CORRECTION: S'assurer qu'on a un nombre pair de bytes
                        int bytesToWrite = byte_lue;
                        if (bytesToWrite % 2 != 0) {
                            bytesToWrite--; // Réduire d'un byte pour avoir un nombre pair
                        }

                        if (bytesToWrite > 0) {
                            sortie_audio.write(buffer, 0, bytesToWrite);
                            audioPacketsReceived++;
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