package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ServeurAudio {
    private static final Logger logger = Logger.getLogger(ServeurAudio.class.getName());

    static final Integer NP_PORT = 5456;
    private static final int SOCKET_TIMEOUT = 30000; // 30 secondes
    private static final int AUDIO_BUFFER_SIZE = 8192; // Buffer audio optimal

    static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    static final Map<String, ClientHandler> ipToClient = Collections.synchronizedMap(new HashMap<>());

    // Statistiques serveur
    private static long totalAudioPackets = 0;
    private static long totalAudioBytes = 0;

    public static void main(String[] args) {
        logger.info("Démarrage du serveur audio sur le port " + NP_PORT);

        // Thread pour afficher les statistiques
        startStatsReporting();

        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            logger.info("Serveur audio en attente de connexions...");

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true); // Critique pour l'audio - réduire latence
                    socket.setSoTimeout(SOCKET_TIMEOUT);
                    socket.setReceiveBufferSize(AUDIO_BUFFER_SIZE);
                    socket.setSendBufferSize(AUDIO_BUFFER_SIZE);

                    logger.info("Nouveau client audio connecté: " + socket.getInetAddress());

                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    ipToClient.put(socket.getInetAddress().toString(), handler);
                    handler.start();

                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Erreur acceptation connexion audio", e);
                }
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur serveur audio", e);
        }
    }

    private static void startStatsReporting() {
        Timer statsTimer = new Timer("AudioServerStats", true);
        statsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (clients) {
                    if (!clients.isEmpty()) {
                        double mbTransferred = totalAudioBytes / (1024.0 * 1024.0);
                        logger.info(String.format("Stats audio: %d clients, %d paquets, %.2f MB transférés",
                                clients.size(), totalAudioPackets, mbTransferred));
                    }
                }
            }
        }, 30000, 30000);
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private DataOutputStream out;
        private DataInputStream in;
        private final String clientIp;
        private volatile boolean running = true;
        private long packetsReceived = 0;
        private long packetsSent = 0;
        private long bytesReceived = 0;
        private long bytesSent = 0;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientIp = socket.getInetAddress().toString();
            setName("AudioClient-" + clientIp.replace("/", ""));
            setPriority(Thread.MAX_PRIORITY); // Priorité élevée pour l'audio
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), AUDIO_BUFFER_SIZE));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), AUDIO_BUFFER_SIZE));

                logger.info("Client audio " + clientIp + " initialisé");

                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        // Lire l'IP de destination
                        String targetIp = in.readUTF();

                        // Boucle de streaming audio
                        byte[] buffer = new byte[1024];

                        while (running && !Thread.currentThread().isInterrupted()) {
                            int bytesRead = in.read(buffer);

                            if (bytesRead == -1) {
                                logger.info("Fin de stream audio pour " + clientIp);
                                break;
                            }

                            if (bytesRead > 0) {
                                packetsReceived++;
                                bytesReceived += bytesRead;
                                totalAudioPackets++;
                                totalAudioBytes += bytesRead;

                                // Transférer immédiatement vers le client cible
                                transferAudioToTarget(targetIp, buffer, bytesRead);
                            }
                        }

                    } catch (SocketTimeoutException e) {
                        logger.fine("Timeout audio pour " + clientIp);
                        break;
                    } catch (IOException e) {
                        if (running) {
                            logger.log(Level.INFO, "Connexion audio fermée par " + clientIp);
                        }
                        break;
                    }
                }

            } catch (IOException e) {
                logger.log(Level.WARNING, "Erreur client audio " + clientIp, e);
            } finally {
                cleanup();
            }
        }

        private void transferAudioToTarget(String targetIp, byte[] buffer, int length) {
            ClientHandler targetClient;
            synchronized (ipToClient) {
                targetClient = ipToClient.get(targetIp);
            }

            if (targetClient != null && targetClient.running && targetClient != this) {
                try {
                    // Envoi immédiat pour minimiser la latence audio
                    synchronized (targetClient.out) {
                        targetClient.out.write(buffer, 0, length);
                        targetClient.out.flush();
                    }

                    packetsSent++;
                    bytesSent += length;
                    targetClient.logReceiveStats();

                } catch (IOException e) {
                    logger.log(Level.WARNING, "Erreur transfert audio vers " + targetIp, e);
                    targetClient.running = false;
                }
            }
        }

        private void logReceiveStats() {
            // Log moins fréquent pour l'audio (toutes les 1000 paquets)
            if (packetsReceived % 1000 == 0) {
                double kbReceived = bytesReceived / 1024.0;
                double kbSent = bytesSent / 1024.0;
                logger.fine(String.format("Client audio %s: %d paquets reçus (%.1f KB), %d envoyés (%.1f KB)",
                        clientIp, packetsReceived, kbReceived, packetsSent, kbSent));
            }
        }

        private void cleanup() {
            running = false;

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Erreur fermeture socket audio", e);
            }

            synchronized (clients) {
                clients.remove(this);
                ipToClient.remove(clientIp);
                logger.info("Client audio déconnecté: " + clientIp +
                        " (paquets reçus: " + packetsReceived + ", envoyés: " + packetsSent + ")");
                logger.info("Clients audio restants: " + clients.size());
            }
        }
    }
}