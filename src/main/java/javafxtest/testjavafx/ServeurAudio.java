package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ServeurAudio {
    private static final Logger logger = Logger.getLogger(ServeurAudio.class.getName());

    static final Integer NP_PORT = 5456;

    static final List<DataOutputStream> clients = Collections.synchronizedList(new ArrayList<>());
    static final List<String> ip_client = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        logger.info("Démarrage du serveur audio sur le port " + NP_PORT);

        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            logger.info("Serveur audio en attente de connexions...");

            while (true) {
                Socket socket = serverSocket.accept();
                logger.info("Nouveau client audio connecté: " + socket.getInetAddress());
                new ClientHandler(socket).start();
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur serveur audio", e);
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
            setName("AudioClient-" + clientIp.replace("/", ""));
            setPriority(Thread.MAX_PRIORITY); // Priorité élevée pour l'audio
        }

        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                    ip_client.add(clientIp);
                }

                logger.info("Client audio " + clientIp + " ajouté. Total: " + clients.size());

                while (true) {
                    String adresse_ip = in.readUTF();

                    byte[] buffer = new byte[1024];
                    int byte_lue;

                    while ((byte_lue = in.read(buffer)) != -1) {
                        // TRANSFERT IMMÉDIAT ET SIMPLE - crucial pour l'audio
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); i++) {
                                if (ip_client.get(i).equals(adresse_ip)) {
                                    try {
                                        clients.get(i).write(buffer, 0, byte_lue);
                                        // PAS de flush() pour éviter la latence
                                    } catch (IOException e) {
                                        // Client déconnecté, on le retire
                                        logger.info("Client audio déconnecté: " + ip_client.get(i));
                                        clients.remove(i);
                                        ip_client.remove(i);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    throw new IOException(); // Force la sortie de la boucle externe
                }

            } catch (IOException e) {
                // Connexion fermée normalement
                logger.fine("Connexion audio fermée: " + clientIp);
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
                logger.log(Level.WARNING, "Erreur fermeture socket audio", e);
            }

            synchronized (clients) {
                clients.remove(out);
                ip_client.remove(clientIp);
                logger.info("Client audio retiré: " + clientIp + ". Restants: " + clients.size());
            }
        }
    }
}