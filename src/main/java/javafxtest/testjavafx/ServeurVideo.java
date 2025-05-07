package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ServeurVideo {
    static final Integer NP_PORT = 5455;

    static final List<DataOutputStream> clients = Collections.synchronizedList(new ArrayList<>());
    static final List<String> ip_client = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            System.out.println("En attente de connexion : ");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nouveau client connecté pour la vidéo");
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
            String adresse_ip;

            try {
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                    ip_client.add(this.socket.getInetAddress().toString());
                    ip_client.set(0, "aaaa");
                }

                System.out.println("Liste des clients pour les appels vidéo " + clients + " " + ip_client);

                while (true) {
                    adresse_ip = in.readUTF();
                    message = in.readUTF();
                    if (message.equals("video")) {
                        while (true) {
                            int length = in.readInt();
                            byte[] data = new byte[length];
                            in.readFully(data);
                            System.out.println("trame reçu...");

                            synchronized (clients) {
                                for (int i = 0; i < ip_client.size(); i++) {
                                    if (ip_client.get(i).equals(adresse_ip)) {
                                        clients.get(i).writeInt(length);
                                        clients.get(i).write(data);
                                        clients.get(i).flush();
                                        System.out.println("Trame envoyé à " + adresse_ip);
                                    }
                                }
                            }
                        }
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
                System.out.println("Liste des clients pour les appels (client et leur ip respectif) " + clients + " " + ip_client);
            }
        }
    }
}
