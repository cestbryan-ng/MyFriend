package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ServeurVideo {
    private static final Logger logger = Logger.getLogger(ServeurVideo.class.getName());

    static final Integer NP_PORT = 5455;

    static final List<DataOutputStream> clients = Collections.synchronizedList(new ArrayList<>());
    static final List<String> ip_client = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        logger.info("Démarrage du serveur vidéo sur le port " + NP_PORT);

        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            logger.info("Serveur vidéo en attente de connexions...");

            while (true) {
                Socket socket = serverSocket.accept();
                logger.info("Nouveau client vidéo connecté: " + socket.getInetAddress());
                new ClientHandler(socket).start();
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur serveur vidéo", e);
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private DataOutputStream out;
        private DataInputStream in;
        private String clientIp;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientIp = socket.getInetAddress().toString();
            setName("VideoClient-" + clientIp.replace("/", ""));
        }

        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                    ip_client.add(clientIp);
                }

                logger.info("Client vidéo " + clientIp + " ajouté. Total: " + clients.size());

                while (true) {
                    String adresse_ip = in.readUTF();

                    while (true) {
                        int length = in.readInt();
                        byte[] data = new byte[length];
                        in.readFully(data);

                        // TRANSFERT DIRECT ET SIMPLE
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); i++) {
                                if (ip_client.get(i).equals(adresse_ip)) {
                                    try {
                                        clients.get(i).writeInt(length);
                                        clients.get(i).write(data);
                                        // PAS de flush() - laisse le système gérer
                                    } catch (IOException e) {
                                        // Client déconnecté, on le retire
                                        logger.info("Client vidéo déconnecté: " + ip_client.get(i));
                                        clients.remove(i);
                                        ip_client.remove(i);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (IOException e) {
                // Connexion fermée normalement
                logger.fine("Connexion vidéo fermée: " + clientIp);
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Erreur fermeture socket vidéo", e);
            }

            synchronized (clients) {
                clients.remove(out);
                ip_client.remove(clientIp);
                logger.info("Client vidéo retiré: " + clientIp + ". Restants: " + clients.size());
            }
        }
    }
}