package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ServeurVideo {
    private static final Logger logger = Logger.getLogger(ServeurVideo.class.getName());

    static final Integer NP_PORT = 5455;
    private static final int MAX_FRAME_SIZE = 1024 * 1024; // 1MB max par frame
    private static final int SOCKET_TIMEOUT = 30000; // 30 secondes
    private static final int BUFFER_SIZE = 5; // Nombre max de frames en buffer

    static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    static final Map<String, ClientHandler> ipToClient = Collections.synchronizedMap(new HashMap<>());

    // Statistiques serveur
    private static long totalFramesTransferred = 0;
    private static long totalBytesTransferred = 0;

    public static void main(String[] args) {
        logger.info("Démarrage du serveur vidéo sur le port " + NP_PORT);

        // Thread pour afficher les statistiques périodiquement
        startStatsReporting();

        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            serverSocket.setSoTimeout(0); // Pas de timeout sur accept()
            logger.info("Serveur vidéo en attente de connexions...");

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true); // Réduire la latence
                    socket.setSoTimeout(SOCKET_TIMEOUT);
                    socket.setReceiveBufferSize(64 * 1024); // 64KB buffer
                    socket.setSendBufferSize(64 * 1024);

                    logger.info("Nouveau client vidéo connecté: " + socket.getInetAddress());

                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    ipToClient.put(socket.getInetAddress().toString(), handler);
                    handler.start();

                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Erreur acceptation connexion", e);
                }
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur serveur vidéo", e);
        }
    }

    private static void startStatsReporting() {
        Timer statsTimer = new Timer("VideoServerStats", true);
        statsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (clients) {
                    if (!clients.isEmpty()) {
                        double mbTransferred = totalBytesTransferred / (1024.0 * 1024.0);
                        logger.info(String.format("Stats vidéo: %d clients, %d frames, %.2f MB transférés",
                                clients.size(), totalFramesTransferred, mbTransferred));
                    }
                }
            }
        }, 30000, 30000); // Toutes les 30 secondes
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private DataOutputStream out;
        private DataInputStream in;
        private final String clientIp;

        // Buffer pour gérer les frames et éviter la congestion
        private final BlockingQueue<VideoFrame> frameBuffer = new LinkedBlockingQueue<>();
        private volatile boolean running = true;
        private long framesReceived = 0;
        private long framesSent = 0;
        private long bytesReceived = 0;
        private long bytesSent = 0;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientIp = socket.getInetAddress().toString();
            setName("VideoClient-" + clientIp.replace("/", ""));
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                logger.info("Client vidéo " + clientIp + " initialisé");

                // Thread séparé pour l'envoi des frames
                Thread senderThread = new Thread(this::frameSender);
                senderThread.setName("VideoSender-" + clientIp.replace("/", ""));
                senderThread.start();

                // Boucle principale de réception
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        String targetIp = in.readUTF();

                        while (running && !Thread.currentThread().isInterrupted()) {
                            int length = in.readInt();

                            // Validation de la taille
                            if (length <= 0 || length > MAX_FRAME_SIZE) {
                                logger.warning("Taille de frame invalide de " + clientIp + ": " + length);
                                continue;
                            }

                            byte[] data = new byte[length];
                            in.readFully(data);

                            framesReceived++;
                            bytesReceived += length;
                            totalFramesTransferred++;
                            totalBytesTransferred += length;

                            // Créer et buffer la frame
                            VideoFrame frame = new VideoFrame(targetIp, data);

                            // Gestion du buffer - drop des vieilles frames si trop plein
                            while (frameBuffer.size() >= BUFFER_SIZE) {
                                VideoFrame dropped = frameBuffer.poll();
                                if (dropped != null) {
                                    logger.fine("Frame droppée pour éviter la congestion");
                                }
                            }

                            frameBuffer.offer(frame);
                        }

                    } catch (SocketTimeoutException e) {
                        logger.fine("Timeout socket pour " + clientIp);
                        break;
                    } catch (IOException e) {
                        if (running) {
                            logger.log(Level.INFO, "Connexion fermée par " + clientIp, e);
                        }
                        break;
                    }
                }

            } catch (IOException e) {
                logger.log(Level.WARNING, "Erreur client vidéo " + clientIp, e);
            } finally {
                cleanup();
            }
        }

        private void frameSender() {
            logger.info("Démarrage sender thread pour " + clientIp);

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    VideoFrame frame = frameBuffer.poll(1, TimeUnit.SECONDS);
                    if (frame != null) {
                        sendFrameToTarget(frame);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erreur envoi frame", e);
                }
            }

            logger.info("Arrêt sender thread pour " + clientIp);
        }

        private void sendFrameToTarget(VideoFrame frame) {
            ClientHandler targetClient;
            synchronized (ipToClient) {
                targetClient = ipToClient.get(frame.targetIp);
            }

            if (targetClient != null && targetClient.running && targetClient != this) {
                try {
                    synchronized (targetClient.out) {
                        targetClient.out.writeInt(frame.data.length);
                        targetClient.out.write(frame.data);
                        targetClient.out.flush();
                    }

                    framesSent++;
                    bytesSent += frame.data.length;
                    targetClient.logReceiveStats();

                } catch (IOException e) {
                    logger.log(Level.WARNING, "Erreur envoi vers " + frame.targetIp, e);
                    // Marquer le client cible comme déconnecté
                    targetClient.running = false;
                }
            }
        }

        private void logReceiveStats() {
            // Log périodique des stats (toutes les 100 frames)
            if (framesReceived % 100 == 0) {
                double mbReceived = bytesReceived / (1024.0 * 1024.0);
                double mbSent = bytesSent / (1024.0 * 1024.0);
                logger.fine(String.format("Client %s: %d frames reçues (%.2f MB), %d envoyées (%.2f MB)",
                        clientIp, framesReceived, mbReceived, framesSent, mbSent));
            }
        }

        private void cleanup() {
            running = false;

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Erreur fermeture socket", e);
            }

            synchronized (clients) {
                clients.remove(this);
                ipToClient.remove(clientIp);
                logger.info("Client vidéo déconnecté: " + clientIp +
                        " (frames reçues: " + framesReceived + ", envoyées: " + framesSent + ")");
                logger.info("Clients vidéo restants: " + clients.size());
            }
        }
    }

    // Classe interne pour représenter une frame vidéo
    private static class VideoFrame {
        final String targetIp;
        final byte[] data;
        final long timestamp;

        VideoFrame(String targetIp, byte[] data) {
            this.targetIp = targetIp;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}