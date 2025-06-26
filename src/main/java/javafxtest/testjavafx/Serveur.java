package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Serveur {
    static final Integer NP_PORT = 8099;

    static final List<DataOutputStream> clients = Collections.synchronizedList(new ArrayList<>());
    static final List<String> ip_client = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            System.out.println("En attente de connexion : ");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nouveau client connecté, adresse du client : " + socket.getInetAddress().toString());
                new ClientHandler(socket).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private DataOutputStream out;
        private DataInputStream in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            String message;
            String adresse_celui_envoye, adresse_type_envoye, nom_celui_envoye;

            try {
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                    ip_client.add(this.socket.getInetAddress().toString());
                }

                System.out.println("Liste des clients actuellement en ligne : " + clients + " " + ip_client);

                while (true) {
                    adresse_celui_envoye = in.readUTF();
                    adresse_type_envoye = in.readUTF();
                    nom_celui_envoye = in.readUTF();
                    message = in.readUTF();

                    if (message.equals("message_fichier")) {
                        System.out.println("Information reçu de  " + adresse_celui_envoye);
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); ++i) {
                                if (ip_client.get(i).equals(adresse_type_envoye)) {
                                    clients.get(i).writeUTF(adresse_celui_envoye);
                                    clients.get(i).writeUTF(nom_celui_envoye);
                                    clients.get(i).writeUTF("message_fichier");
                                    System.out.println("Information envoyé à " + adresse_type_envoye);
                                }
                            }
                        }
                    } else if (message.equals("audio")) {
                        System.out.println("Information reçu de  " + adresse_celui_envoye);
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); ++i) {
                                if (ip_client.get(i).equals(adresse_type_envoye)) {
                                    clients.get(i).writeUTF(adresse_celui_envoye);
                                    clients.get(i).writeUTF(nom_celui_envoye);
                                    clients.get(i).writeUTF("audio");
                                    System.out.println("Information envoyé à " + adresse_type_envoye);
                                }
                            }
                        }
                    }  else if (message.equals("video")) {
                        System.out.println("Information reçu de  " + adresse_celui_envoye);
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); ++i) {
                                if (ip_client.get(i).equals(adresse_type_envoye)) {
                                    clients.get(i).writeUTF(adresse_celui_envoye);
                                    clients.get(i).writeUTF(nom_celui_envoye);
                                    clients.get(i).writeUTF("video");
                                    System.out.println("Information envoyé à " + adresse_type_envoye);
                                }
                            }
                        }
                    }
                }

            } catch (IOException e) {}
            finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clients) {
                    System.out.println("Client déconnecté.");
                    clients.remove(out);
                    ip_client.remove(this.socket.getInetAddress().toString());
                }
                System.out.println("Liste des clients actuellement en ligne : " + clients + " " + ip_client);
            }
        }
    }
}
