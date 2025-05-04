package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class Serveur {
    static final Integer NP_PORT = 5454;

    private static final List<DataOutputStream> clients = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> ip_client = Collections.synchronizedList(new ArrayList<>());

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
            Scanner scanner = new Scanner(System.in);
            String message;
            String adresse_ip;

            try {
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                    ip_client.add(this.socket.getInetAddress().toString());
                }

                System.out.println("Liste des clients " + clients + " " + ip_client);

                while (true) {

                    adresse_ip = in.readUTF();
                    if (!(ip_client.contains(adresse_ip))) continue;
                    message = in.readUTF();
                    if (message.equals("message")) {
                        message = in.readUTF();
                        System.out.println("Message recu : " + message + "à envoyer à l'adresse : " + adresse_ip);
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); ++i) {
                                if (ip_client.get(i).equals(adresse_ip)) {
                                    clients.get(i).writeUTF("message");
                                    clients.get(i).writeUTF(message);
                                    System.out.println("Message envoyé à " + adresse_ip + ": " + message);
                                }
                            }
                        }

                    } else if (message.equals("fichier")) {
                        if (!(ip_client.contains(adresse_ip))) continue;
                        String nom_fichier = in.readUTF();
                        Long taille_fichier = in.readLong();
                        FileOutputStream fichier_recu = new FileOutputStream(nom_fichier);

                        // On recupere le fichier
                        byte[] buffer = new byte[65536];
                        int bytesLues;
                        while ((bytesLues = in.read(buffer, 0, (int) Math.min(buffer.length, taille_fichier))) != 0) {
                            System.out.println("reçu : " + bytesLues + "/" + taille_fichier + " (octets)");
                            fichier_recu.write(buffer, 0, bytesLues);
                            taille_fichier -= bytesLues;
                        }
                        fichier_recu.flush();
                        System.out.println("Fichier recu : " + nom_fichier);

                        FileInputStream fichier_envoie = new FileInputStream(nom_fichier);
                        buffer = new byte[65536];
                        synchronized (clients) {
                            for (int i = 0; i < ip_client.size(); i++) {
                                if (ip_client.get(i).equals(adresse_ip)) {
                                    clients.get(i).writeUTF("fichier");
                                    clients.get(i).writeUTF(nom_fichier);
                                    clients.get(i).writeLong(new File(nom_fichier).length());

                                    // Pour l'envoie de fichier en faisant du handshake
                                    while ((bytesLues = fichier_envoie.read(buffer)) != -1) {
                                        System.out.println("envoyé : " + bytesLues +  " octects");
                                        clients.get(i).write(buffer, 0, bytesLues);
                                    }
                                }
                            }
                        }
                        fichier_envoie.close();
                        System.out.println("Fichier envoyé à "+ adresse_ip +" : " + nom_fichier);

                    } else if (message.equals("video")) {

                    }
                }



            } catch (IOException e) {

            } finally {
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
                System.out.println("Liste des clients (client et leur ip respectif) " + clients + " " + ip_client);
            }
        }
    }
}
