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
    private static boolean appelEnCours = true; // Nouveau flag pour la sonnerie
    private static TargetDataLine micro;
    private static final AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
    private static final DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    private Thread threadEmission, threadReception, threadTimer, threadSonnerie;

    private Stage stage;
    private Clip sonnerieClip; // Pour jouer la sonnerie

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encours = true;
        appelEnCours = true;
        nom_recepteur.setText(MainPageController.recepteur_audio);
        Platform.runLater(() -> stage = (Stage) root.getScene().getWindow());

        // Démarrer la sonnerie d'appel
        demarrerSonnerie();

        threadReception = new Thread(this::recevoir);
        threadReception.start();

        try {
            Page1Controller.out_audio.writeUTF(MainPageController.adressre_recepteur_audio);
            micro = (TargetDataLine) AudioSystem.getLine(info);
            micro.open(format);
            micro.start();
        } catch (LineUnavailableException | IOException e) {
            encours = false;
            appelEnCours = false;
            arreterSonnerie();
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
                    appelEnCours = false;
                    arreterSonnerie();
                    fermerFenetre();
                    break;
                }
            }
        });
        threadEmission.setDaemon(true);
        threadEmission.start();
    }

    private void demarrerSonnerie() {
        threadSonnerie = new Thread(() -> {
            try {
                // Charger le fichier de sonnerie (vous devez avoir ce fichier dans vos ressources)
                URL sonnerieUrl = getClass().getResource("telephone-ring-04.wav");
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
                // En cas d'erreur, générer une sonnerie simple
                genererSonnerieSimple();
            }
        });
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
                // Générer un bip de 800Hz pendant 0.5 seconde
                byte[] bipData = genererBip(800, 0.5, formatBip);
                line.write(bipData, 0, bipData.length);

                // Pause de 1 seconde
                if (appelEnCours && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }

                // Deuxième bip
                line.write(bipData, 0, bipData.length);

                // Pause plus longue entre les cycles
                if (appelEnCours && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(2000);
                }
            }

            line.drain();
            line.close();
        } catch (LineUnavailableException | InterruptedException e) {
            // Ignorer les erreurs
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

        if (threadSonnerie != null) {
            threadSonnerie.interrupt();
        }

        if (sonnerieClip != null && sonnerieClip.isRunning()) {
            sonnerieClip.stop();
            sonnerieClip.close();
        }
    }

    @FXML
    private void raccrocher() {
        encours = false;
        appelEnCours = false;
        arreterSonnerie();
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

        try (SourceDataLine sortie_audio = AudioSystem.getSourceDataLine(format)) {
            sortie_audio.open(format);
            sortie_audio.start();
            byte[] buffer = new byte[1024];

            while (encours) {
                try {
                    int byte_lue = Page1Controller.in_audio.read(buffer);
                    sortie_audio.write(buffer, 0, byte_lue);
                    if (!(threadTimer.isAlive())) {
                        // L'autre personne a décroché, arrêter la sonnerie
                        appelEnCours = false;
                        arreterSonnerie();

                        threadTimer.start();
                        Page1Controller.socket_audio.setSoTimeout(500);
                    }
                } catch (SocketTimeoutException e) {
                    encours = false;
                    appelEnCours = false;
                    arreterSonnerie();
                    fermerFenetre();
                }
            }
        } catch (IOException | LineUnavailableException e) {
            encours = false;
            appelEnCours = false;
            arreterSonnerie();
        } finally {
            fermerFenetre();
        }
    }

    private void fermerFenetre() {
        appelEnCours = false;
        arreterSonnerie();

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
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            if (stage == null) stage = (Stage) root.getScene().getWindow();
            if (stage != null) stage.close();
        });
    }
}