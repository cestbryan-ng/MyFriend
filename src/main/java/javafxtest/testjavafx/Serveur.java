package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Serveur {
    static final Integer NP_PORT = 5454;
    private static final Set<DataOutputStream> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            System.out.println("En attente de connexion : ");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nouveau client connecté, adresse du client : " + socket.getInetAddress());
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
            Scanner scanner = new Scanner(System.in);
            String message;

            try {
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                }

                while (true) {
                    message = in.readUTF();
                    if (message.equals("message")) {
                        message = in.readUTF();
                        System.out.println("Message recu de (Adresse inconnue) : " + message);
                        synchronized (clients) {
                            for (DataOutputStream client : clients) {
                                client.writeUTF("message");
                                client.writeUTF(message);
                            }
                            System.out.println("Message envoyé aux autres clients : " + message);
                        }

                    } else if (message.equals("fichier")) {
                        String nom_fichier = in.readUTF();
                        Long taille_fichier = in.readLong();
                        FileOutputStream fichier_recu = new FileOutputStream(nom_fichier);

                        // On recupere le fichier
                        byte[] buffer = new byte[65536];
                        int bytesLues;
                        while ((bytesLues = in.read(buffer, 0, (int) Math.min(buffer.length, taille_fichier))) != 0) {
                            System.out.println(bytesLues);
                            fichier_recu.write(buffer, 0, bytesLues);
                            taille_fichier -= bytesLues;
                        }

                        fichier_recu.flush();
                        System.out.println("Fichier recu de (Adresse  inconnue) : " + nom_fichier);

                        FileInputStream fichier_envoie = new FileInputStream(nom_fichier);
                        buffer = new byte[65536];
                        synchronized (clients) {
                            for (DataOutputStream client : clients) {
                                client.writeUTF("fichier");
                                client.writeUTF(nom_fichier);
                                client.writeLong(new File(nom_fichier).length());

                                // Pour l'envoie de fichier en faisant du hanshake
                                while ((bytesLues = fichier_envoie.read(buffer)) != -1) {
                                    System.out.println(bytesLues);
                                    client.write(buffer, 0, bytesLues);
                                }
                            }
                        }
                        fichier_envoie.close();
                        System.out.println("Fichier envoyé aux autres clients : " + nom_fichier);
                    }
                }

            } catch (IOException e) {
                System.out.println("Client déconnecté.");

            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clients) {
                    clients.remove(out);
                }
            }
        }
    }
}
