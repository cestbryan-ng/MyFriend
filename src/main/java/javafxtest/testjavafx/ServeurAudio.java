package javafxtest.testjavafx;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ServeurAudio {
    static final Integer NP_PORT = 5456;

    static final List<DataOutputStream> clients = Collections.synchronizedList(new ArrayList<>());
    static final List<String> ip_client = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(NP_PORT)) {
            System.out.println("En attente de connexion : ");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nouveau client connecté pour l'appel");
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
            String adresse_ip = "";

            try {
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                synchronized (clients) {
                    clients.add(out);
                    ip_client.add(this.socket.getInetAddress().toString());
                }

                System.out.println("Liste des clients pour les appels audios : " + clients + " " + ip_client);

                while (true) {
                    adresse_ip = in.readUTF();
                    while (true) {
                        byte[] buffer = new byte[4096];
                        int byte_lue;
                        while ((byte_lue = in.read(buffer)) != -1) {
                            System.out.println("Audio reçu...");
                            synchronized (clients) {
                                for (int i = 0; i < ip_client.size(); i++) {
                                    if (ip_client.get(i).equals(adresse_ip)) {
                                        clients.get(i).write(buffer, 0, byte_lue);
                                        System.out.println("Audio envoyé à " + adresse_ip);
                                    }
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
                System.out.println("Liste des clients pour les appels : " + clients + " " + ip_client);
            }
        }
    }
}
