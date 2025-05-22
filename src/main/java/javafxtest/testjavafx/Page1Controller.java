package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.net.URL;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class Page1Controller implements Initializable {
    static List<String> liste_nom = new ArrayList<>();
    static String recepteur = "";
    static String adresse_recepteur = "";
    private static final String ADRESSE_SERVEUR = "localhost";
    static Socket socket;
    static DataOutputStream out;
    static DataInputStream in;
    static Socket socket_audio;
    static DataInputStream in_audio;
    static DataOutputStream out_audio;

    @FXML
    private AnchorPane anchorpane1;

    @FXML
    private TextField message_envoyer;

    @FXML
    private Label nom_utilisateur;

    @FXML
    private TextField recherche_conversation;

    @FXML
    private ImageView profil_enligne;

    @FXML
    private VBox vbox1;

    @FXML
    private VBox vbox2;

    @FXML
    private Label enligne;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        new Thread(this::Recevoir).start();

        enligne.setText("");
        vbox1.getChildren().clear();
        vbox2.getChildren().clear();

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select username from user\n" +
                    "where username != \""+ MainPageController.nomutilisateur +"\";");
            while (resultSet.next()) {
                Button button = new Button(resultSet.getString(1));
                button.setPrefSize(191, 47);
                button.setGraphicTextGap(20);
                ImageView imageView = new ImageView(new Image(getClass().getResource("images/cercle1.png").toString()));
                imageView.setFitWidth(36);
                imageView.setFitHeight(47);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
                button.setStyle("-fx-text-fill : white; -fx-border-color : gray; -fx-border-width : 0 0 0.1 0; -fx-cursor : hand;");
                button.setOnAction(event -> Charger(event));
                vbox1.getChildren().add(button);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void Appel() throws IOException {
        if (adresse_recepteur.equals("")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Personne trouvée");
            alert.setContentText("Choisir une personne");
            alert.show();
            return;
        }

        String indice_connexion = "offline";

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet1 = stmt.executeQuery("select statut from connected_user\n" +
                    "where user_id in (\n" +
                    "select user_id from user\n" +
                    "where username = \""+ recepteur +"\");");
            while(resultSet1.next()) {
                indice_connexion = resultSet1.getString(1);
            }
            resultSet1.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (indice_connexion.equals("offline")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Pas possible d'effectuer l'appel");
            alert.setContentText("L'utilisateur n'est pas en ligne");
            alert.show();
            return;
        }

        try {
            socket_audio = new Socket(ADRESSE_SERVEUR, ServeurAudio.NP_PORT);
            in_audio = new DataInputStream(socket_audio.getInputStream());
            out_audio = new DataOutputStream(socket_audio.getOutputStream());

            MainPageController.out.writeUTF(adresse_recepteur);
            MainPageController.out.writeUTF("message");
            MainPageController.out.writeUTF("123456789abcdefgh=" + MainPageController.nomutilisateur + "=appel");
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Erreur");
            alert.setContentText("L'appel n'a pas pu démarrer");
            alert.show();
            return;
        }

        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1Appel.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 300, 300);
        scene.getStylesheets().add(getClass().getResource("Page1Appel.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
        Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
        stage1.close();
    }

    @FXML
    void Envoie(ActionEvent event) {
        if (adresse_recepteur.equals("")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Personne trouvée");
            alert.setContentText("Choisir une personne");
            alert.show();
            return;
        }

        if (message_envoyer.getText().equals("")) return;

        try {
            Integer sender_id = 0, recever_id = 0;
            String indice_connexion = "offline";

            try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
                ResultSet resultSet1 = stmt.executeQuery("select statut from connected_user\n" +
                        "where user_id in (\n" +
                        "select user_id from user\n" +
                        "where username = \""+ recepteur +"\");");
                while (resultSet1.next()) {
                    indice_connexion = resultSet1.getString(1);
                }
                resultSet1.close();

                ResultSet resultSet2 = stmt.executeQuery("select connected_userid from connected_user\n" +
                        "where user_id in \n" +
                        "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\") ;");
                while (resultSet2.next()) {
                    sender_id = resultSet2.getInt(1);
                }
                resultSet2.close();

                ResultSet resultSet3 = stmt.executeQuery("select connected_userid from connected_user\n" +
                        "where user_id in \n" +
                        "(select user_id from user where username = \""+ recepteur +"\") ;");
                while (resultSet3.next()) {
                    recever_id = resultSet3.getInt(1);
                }
                resultSet3.close();

                stmt.executeUpdate("insert into message(sender_id, recever_id, content)\n" +
                        "values (\""+ sender_id +"\", \""+ recever_id +"\", \""+ message_envoyer.getText() +"\");");

            } catch (SQLException e) {
                e.printStackTrace();
            }

            if (indice_connexion.equals("offline")) {
                Label label = new Label();
                label.setText(message_envoyer.getText());
                label.setPrefHeight(25);
                label.setAlignment(Pos.BASELINE_CENTER);
                label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
                label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : #e7961c; -fx-background-radius : 20;");
                HBox hBox = new HBox();
                vbox2.getChildren().add(hBox);
                hBox.getChildren().add(label);
                hBox.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(label, new Insets(10, 0, 0, 10));
                message_envoyer.deleteText(0, message_envoyer.getText().length());
                return;
            }

            MainPageController.out.writeUTF(adresse_recepteur);
            MainPageController.out.writeUTF("message");
            MainPageController.out.writeUTF(message_envoyer.getText());
            Label label = new Label();
            label.setText(message_envoyer.getText());
            label.setPrefHeight(25);
            label.setAlignment(Pos.BASELINE_CENTER);
            label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : #e7961c; -fx-background-radius : 20;");
            HBox hBox = new HBox();
            vbox2.getChildren().add(hBox);
            hBox.getChildren().add(label);
            hBox.setAlignment(Pos.CENTER_RIGHT);
            HBox.setMargin(label, new Insets(10, 0, 0, 10));
            message_envoyer.deleteText(0, message_envoyer.getText().length());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void Recevoir() {
        try {
            while (true) {
                String type_envoe = MainPageController.in.readUTF();
                
                if (type_envoe.equals("message")) {
                    String message_recu = "";
                    try {
                        message_recu = MainPageController.in.readUTF();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String finalMessage_recu = message_recu;

                    if (finalMessage_recu.split("=")[0].equals("123456789abcdefgh")) {
                        if (finalMessage_recu.split("=")[2].equals("video")) {
                            if (Page1VideoController.encours) continue;
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                                alert.setHeaderText(finalMessage_recu.split("=")[1] + " vous appelle");
                                alert.setContentText("Appuyer sur OK pour décrocher");
                                Optional<ButtonType> result = alert.showAndWait();
                                if (result.isPresent() && result.get() == ButtonType.OK) {
                                    recepteur = finalMessage_recu.split("=")[1];
                                    adresse_recepteur = "rien";
                                    try {
                                        Video();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            });
                            continue;
                        } else {
                            if (Page1AppelController.encours) continue;
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                                alert.setHeaderText(finalMessage_recu.split("=")[1] + " vous appelle");
                                alert.setContentText("Appuyer sur OK pour décrocher");
                                Optional<ButtonType> result = alert.showAndWait();
                                if (result.isPresent() && result.get() == ButtonType.OK) {
                                    recepteur = finalMessage_recu.split("=")[1];
                                    adresse_recepteur = "rien";
                                    try {
                                        Appel();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            });
                            continue;
                        }
                    }

                    Platform.runLater(() -> {
                        Label label = new Label();
                        label.setText(finalMessage_recu);
                        label.setPrefHeight(25);
                        label.setAlignment(Pos.BASELINE_CENTER);
                        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
                        label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : lightgreen; -fx-background-radius : 20;");
                        HBox hBox = new HBox();
                        vbox2.getChildren().add(hBox);
                        hBox.getChildren().add(label);
                        HBox.setMargin(label, new Insets(10, 0, 0, 10));
                    });

                } else if (type_envoe.equals("fichier")) {
                    String nom_fichier = MainPageController.in.readUTF();
                    long taille_fichier = MainPageController.in.readLong();

                    FileOutputStream fichier_recu = new FileOutputStream(nom_fichier);
                    // On recupere le fichier
                    byte[] buffer = new byte[65536];
                    int bytesLues;
                    while ((bytesLues = MainPageController.in.read(buffer, 0, (int) Math.min(buffer.length, taille_fichier))) != 0) {
                        System.out.println(bytesLues);
                        fichier_recu.write(buffer, 0, bytesLues);
                        taille_fichier -= bytesLues;
                    }
                    System.out.println("reçu avec succès");
                    fichier_recu.close();

                    Platform.runLater(() -> {
                        Label label = new Label();
                        label.setText("Fichier reçu sous le nom de : " + nom_fichier);
                        label.setPrefHeight(25);
                        label.setAlignment(Pos.BASELINE_CENTER);
                        label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
                        label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : lightgreen; -fx-background-radius : 20;");
                        HBox hBox = new HBox();
                        vbox2.getChildren().add(hBox);
                        hBox.getChildren().add(label);
                        HBox.setMargin(label, new Insets(10, 0, 0, 10));
                    });
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void Fermer() throws SQLException, IOException {
        MainPageController.in.close();
        MainPageController.out.close();
        MainPageController.socket.close();

        Connection  connection = BaseDeDonnee.seConnecter();
        Statement stmt = connection.createStatement();
        stmt.executeUpdate("update connected_user\n" +
                "set statut = \"offline\"\n" +
                "where user_id in (\n" +
                "select user_id from user where username = \""+ MainPageController.nomutilisateur +"\");");

        stmt.executeUpdate("update connected_user\n" +
                "set last_connection = current_timestamp\n" +
                "where user_id in\n" +
                "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\");");

        stmt.close();
        connection.close();
        Stage stage = (Stage) anchorpane1.getScene().getWindow();
        stage.close();
    }

    @FXML
    void Fichier(ActionEvent event) throws IOException {
        if (adresse_recepteur.equals("")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Personne trouvée");
            alert.setContentText("Choisir une personne");
            alert.show();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        Stage stage = (Stage) anchorpane1.getScene().getWindow();
        File fichier = fileChooser.showOpenDialog(stage);

        if (fichier != null) {
            if (fichier.length() > 256000) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Trop volumineux");
                alert.setContentText("Envoyer des fichiers de moins de 256Ko");
                alert.showAndWait();
                return;
            }

            Integer sender_id = 0, recever_id = 0;
            String indice_connexion = "";

            try {
                FileInputStream fichier_envoie = new FileInputStream(fichier);

                try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
                    ResultSet resultSet1 = stmt.executeQuery("select statut from connected_user\n" +
                            "where user_id in (\n" +
                            "select user_id from user\n" +
                            "where username = \""+ recepteur +"\");");
                    while (resultSet1.next()) {
                        indice_connexion = resultSet1.getString(1);
                    }
                    resultSet1.close();
                    ResultSet resultSet2 = stmt.executeQuery("select connected_userid from connected_user\n" +
                            "where user_id in \n" +
                            "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\") ;");
                    while (resultSet2.next()) {
                        sender_id = resultSet2.getInt(1);
                    }
                    resultSet2.close();

                    ResultSet resultSet3 = stmt.executeQuery("select connected_userid from connected_user\n" +
                            "where user_id in \n" +
                            "(select user_id from user where username = \""+ recepteur +"\") ;");
                    while (resultSet3.next()) {
                        recever_id = resultSet3.getInt(1);
                    }
                    resultSet3.close();

                    stmt.executeUpdate("insert into message(sender_id, recever_id, content)\n" +
                            "values (\""+ sender_id +"\", \""+ recever_id +"\", \"Fichier "+ fichier.getName() +"\");");
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                if (indice_connexion.equals("offline")) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Oups");
                    alert.setContentText("Nous ne pouvons pas encore envoyer de fichier à un utilisateur hors ligne");
                    alert.show();
                    return;

//                    Label label = new Label();
//                    label.setText("Fichier " + fichier.getName() + " envoyé");
//                    label.setPrefHeight(25);
//                    label.setAlignment(Pos.BASELINE_CENTER);
//                    label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
//                    label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : #e7961c; -fx-background-radius : 20;");
//                    HBox hBox = new HBox();
//                    vbox2.getChildren().add(hBox);
//                    hBox.getChildren().add(label);
//                    hBox.setAlignment(Pos.CENTER_RIGHT);
//                    HBox.setMargin(label, new Insets(10, 0, 0, 10));
//                    message_envoyer.deleteText(0, message_envoyer.getText().length());
//                    return;
                }

                MainPageController.out.writeUTF(adresse_recepteur);
                MainPageController.out.writeUTF("fichier");
                MainPageController.out.writeUTF(fichier.getName());
                MainPageController.out.writeLong(fichier.length());

                // Pour l'envoie de fichier en faisant du handshake
                byte[] buffer = new byte[65536];
                int bytesLues;
                while ((bytesLues = fichier_envoie.read(buffer)) != -1) {
                    MainPageController.out.write(buffer, 0, bytesLues);
                }

                Label label = new Label();
                label.setText("Fichier " + fichier.getName() + " envoyé");
                label.setPrefHeight(25);
                label.setAlignment(Pos.BASELINE_CENTER);
                label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
                label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : #e7961c; -fx-background-radius : 20;");
                HBox hBox = new HBox();
                vbox2.getChildren().add(hBox);
                hBox.getChildren().add(label);
                hBox.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(label, new Insets(10, 0, 0, 10));
                message_envoyer.deleteText(0, message_envoyer.getText().length());
                fichier_envoie.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else System.out.println("Fichier inexistant");
    }

    @FXML
    void Video() throws IOException {
        if (adresse_recepteur.equals("")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Personne trouvée");
            alert.setContentText("Choisir une personne");
            alert.show();
            return;
        }

        String indice_connexion = "offline";

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet1 = stmt.executeQuery("select statut from connected_user\n" +
                    "where user_id in (\n" +
                    "select user_id from user\n" +
                    "where username = \""+ recepteur +"\");");
            while(resultSet1.next()) {
                indice_connexion = resultSet1.getString(1);
            }
            resultSet1.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (indice_connexion.equals("offline")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Pas possible d'effectuer l'appel");
            alert.setContentText("L'utilisateur n'est pas en ligne");
            alert.show();
            return;
        }

        try {
            socket = new Socket(ADRESSE_SERVEUR, ServeurVideo.NP_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            socket_audio = new Socket(ADRESSE_SERVEUR, ServeurAudio.NP_PORT);
            in_audio = new DataInputStream(socket_audio.getInputStream());
            out_audio = new DataOutputStream(socket_audio.getOutputStream());

            MainPageController.out.writeUTF(adresse_recepteur);
            MainPageController.out.writeUTF("message");
            MainPageController.out.writeUTF("123456789abcdefgh=" + MainPageController.nomutilisateur + "=video");
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Erreur");
            alert.setContentText("L'appel n'a pas pu démarrer");
            alert.show();
            return;
        }

        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1Video.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 950, 410);
        scene.getStylesheets().add(getClass().getResource("Page1VideoUI.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
        Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
        stage1.close();
    }

    @FXML
    void Charger(ActionEvent event) {
        vbox2.getChildren().clear();

        Button button_clique = (Button) event.getSource();
        String indice_de_connexion = "offline";
        String heure_derniere_connexion = "";
        recepteur = button_clique.getText();
        nom_utilisateur.setText(recepteur);
        Integer self = 0, other = 0;

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet1 = stmt.executeQuery("select statut from connected_user\n" +
                    "where user_id in (\n" +
                    "select user_id from user\n" +
                    "where username = \""+ recepteur +"\");");
            while(resultSet1.next()) {
                indice_de_connexion = resultSet1.getString(1);
            }
            resultSet1.close();

            ResultSet resultSet2 = stmt.executeQuery("select connected_userid from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\") ;");
            while (resultSet2.next()) {
                self = resultSet2.getInt(1);
            }
            resultSet2.close();

            ResultSet resultSet3 = stmt.executeQuery("select connected_userid from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ recepteur +"\") ;");
            while (resultSet3.next()) {
                other = resultSet3.getInt(1);
            }
            resultSet3.close();

            ResultSet resultSet5 = stmt.executeQuery("select adresse_ip from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ recepteur +"\");");
            while (resultSet5.next()) {
                adresse_recepteur = resultSet5.getString(1);
                adresse_recepteur = "/" + adresse_recepteur.split("/")[1];
            }
            resultSet5.close();

            ResultSet resultSet4 = stmt.executeQuery("select sender_id, recever_id, content from message\n" +
                    "where sender_id in ("+ self +", "+ other +")\n" +
                    "and recever_id in ("+ self +", "+ other +") order by time_send asc;");
            while (resultSet4.next()) {
                if (self.equals(resultSet4.getInt(1))) {
                    Label label = new Label();
                    label.setText(resultSet4.getString(3));
                    label.setPrefHeight(25);
                    label.setAlignment(Pos.BASELINE_CENTER);
                    label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
                    label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : #e7961c; -fx-background-radius : 20;");
                    HBox hBox = new HBox();
                    vbox2.getChildren().add(hBox);
                    hBox.getChildren().add(label);
                    hBox.setAlignment(Pos.CENTER_RIGHT);
                    HBox.setMargin(label, new Insets(10, 0, 0, 10));
                } else {
                    Label label = new Label();
                    label.setText(resultSet4.getString(3));
                    label.setPrefHeight(25);
                    label.setAlignment(Pos.BASELINE_CENTER);
                    label.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
                    label.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14; -fx-font-weight : bold; -fx-background-color : lightgreen; -fx-background-radius : 20;");
                    HBox hBox = new HBox();
                    vbox2.getChildren().add(hBox);
                    hBox.getChildren().add(label);
                    HBox.setMargin(label, new Insets(10, 0, 0, 10));
                }
            }
            resultSet4.close();

            if (indice_de_connexion.equals("offline")) {
                stmt.executeUpdate("set lc_time_names = 'fr_FR'; ");
                ResultSet resultSet6 = stmt.executeQuery("select date_format(last_connection, '%d/%m/%Y à %H:%i')\n" +
                        "from connected_user\n" +
                        "where user_id in\n" +
                        "(select user_id from user where username = \""+ recepteur +"\");");

                while (resultSet6.next()) {
                    heure_derniere_connexion = resultSet6.getString(1);
                }
                resultSet6.close();
                enligne.setText("En ligne : " + heure_derniere_connexion);
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }


        if (indice_de_connexion.equals("online")) {
            enligne.setText("Actuellement en ligne");
            profil_enligne.setImage(new Image(getClass().getResource("images/rondvert.png").toString()));
        }
        else profil_enligne.setImage(new Image(getClass().getResource("images/rondrouge.png").toString()));
    }

    @FXML
    void Rechercher() {
        vbox1.getChildren().clear();

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select username from user\n" +
                    "where username like \"%"+ recherche_conversation.getText().toLowerCase() +"%\"" +
                    "and username != \""+ MainPageController.nomutilisateur +"\";");
            while (resultSet.next()) {
                Button button = new Button(resultSet.getString(1));
                button.setPrefSize(191, 47);
                button.setGraphicTextGap(20);
                ImageView imageView = new ImageView(new Image(getClass().getResource("images/cercle1.png").toString()));
                imageView.setFitWidth(36);
                imageView.setFitHeight(47);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
                button.setStyle("-fx-text-fill : white; -fx-border-color : gray; -fx-border-width : 0 0 0.1 0; -fx-cursor : hand;");
                button.setOnAction(event -> Charger(event));
                vbox1.getChildren().add(button);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
