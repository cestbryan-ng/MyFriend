package test1;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Serveur {
    private static final int PORT = 5000;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Serveur en attente de connexions...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Une connexion réussie.");
                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Classe pour gérer les messages d'un client
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                String message;
                while ((message = input.readUTF()) != null) {
                    if (!(message.equals("fichier"))) {   // N'est pas un fichier
                        // Diffuser le message à tous les autres clients
                        synchronized (clients) {
                            for (ClientHandler client : clients) {
                                if (client != this) {
                                    client.output.writeUTF(message);
                                }
                            }
                        }
                        System.out.println("Message reçu et envoyé : " + message);
                    } else { // Est un fichier
                        //Reception du fichier
                        String nomfichier = input.readUTF();
                        long taillefichier = input.readLong();

                        //Creation de fichier pour recevoir les données
                        FileOutputStream fos = new FileOutputStream("reçu_" + nomfichier);
                        byte[] buffer = new byte[4096];
                        int lecture_octet;     
                        
                        //Reception du fichier par morceaux et l'écrire dans le fichier crée
                        while (taillefichier > 0 && (lecture_octet = input.read(buffer, 0, (int) Math.min(buffer.length, taillefichier))) != -1) {
                            fos.write(buffer, 0, lecture_octet);
                            taillefichier -= lecture_octet;
                        }
                        fos.close();
                        

                        //Rediriger le fichier aux clients
                        FileInputStream fis = new FileInputStream("reçu_" + nomfichier);
                        buffer = new byte[4096];
                        synchronized (clients) {
                            for (ClientHandler client : clients) {
                                if (client != this) {
                                    client.output.writeUTF("fichier");
                                    client.output.writeUTF(nomfichier);
                                    client.output.writeLong(new File("reçu_" + nomfichier).length());

                                    //Envoie du fichier
                                    while ((lecture_octet = fis.read(buffer)) != -1) {
                                        client.output.write(buffer, 0, lecture_octet);
                                    }
                                }                              
                            }
                        }
                        System.out.println("Fichier reçu et envoyé : " + nomfichier);
                        fis.close();
                    } 
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
