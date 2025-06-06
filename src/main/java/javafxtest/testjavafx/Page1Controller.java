package javafxtest.testjavafx;

import javafx.animation.PauseTransition;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.net.URL;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class Page1Controller implements Initializable {
    static Socket socket_video;
    static DataOutputStream out_video;
    static DataInputStream in_video;
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
    private HBox hbox1;

    @FXML
    private VBox vbox1;

    @FXML
    private VBox vbox2;

    @FXML
    private Label enligne;

    @FXML
    private Button button_appel;

    @FXML
    private Button button_info;

    @FXML
    private Button button_envoyer;

    @FXML
    private Button button_video;

    @FXML
    private Button button_retirer;

    @FXML
    private Button button_fichier;

    @FXML
    private Button button_emoji;


    @FXML
    private Popup emojiPopup;

    @FXML
    private Popup messagePopup;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        new Thread(this::Recevoir).start();

        String[] emojis = {"👍", "💘", "👎", "😂", "😎", "😅", "🖖"};
        emojiPopup = new Popup();
        FlowPane emojiPane = new FlowPane();
        emojiPane.setStyle("-fx-background-color : \"lightblue\" ");

        for (String emoji : emojis) {
            Button button = new Button(emoji);
            button.setOnAction(e -> {
                message_envoyer.appendText(emoji);
                emojiPopup.hide();
            });
            emojiPane.getChildren().add(button);
        }
        emojiPopup.getContent().add(emojiPane);
        emojiPopup.setAutoHide(true);

        button_appel.setDisable(true); button_fichier.setDisable(true); button_envoyer.setDisable(true); button_video.setDisable(true); button_info.setDisable(true); button_retirer.setDisable(true); button_emoji.setDisable(true);
        nom_utilisateur.setText("Choissisez un contact");
        enligne.setText("Il s'agit de l'indice de connexion");
        vbox1.getChildren().clear();
        vbox2.getChildren().clear();

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select user.username from\n" +
                    "contact join user on user.user_id = contact.contact_user_id\n" +
                    "where contact.user_id in\n" +
                    "(select user_id from user\n" +
                    "where username = \""+ MainPageController.nomutilisateur +"\");");

            int numero = 0;
            while (resultSet.next()) {
                Button button = new Button(resultSet.getString(1));
                button.setPrefSize(191, 47);
                button.setGraphicTextGap(20);
                ImageView imageView = new ImageView(new Image(getClass().getResource("images/cercle"+ numero +".png").toString()));
                imageView.setFitWidth(36);
                imageView.setFitHeight(47);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
                button.setStyle("-fx-text-fill : white; -fx-border-color : gray; -fx-border-width : 0 0 0.1 0; -fx-cursor : hand;");
                button.setOnAction(event -> Charger(event));
                vbox1.getChildren().add(button);
                vbox1.setAlignment(Pos.TOP_CENTER);

                if (numero == 0) numero++;
                else numero--;
            }
            resultSet.close();

        } catch (SQLException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec de chargement");
            alert.setContentText("Nous n'avons pas pu charger vos contacts, redémarrer l'app.");
            alert.showAndWait();
        }
    }

    @FXML
    void Appel() throws IOException {
        MainPageController.adressre_recepteur_audio = MainPageController.adresse_recepteur;
        MainPageController.recepteur_audio = MainPageController.recepteur;

        if (MainPageController.enligne.equals("offline")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Pas possible d'effectuer l'appel");
            alert.setContentText("L'utilisateur n'est pas en ligne");
            alert.show();
            return;
        }

        try {
            socket_audio = new Socket(MainPageController.ADRESSE_SERVEUR, ServeurAudio.NP_PORT);
            in_audio = new DataInputStream(socket_audio.getInputStream());
            out_audio = new DataOutputStream(socket_audio.getOutputStream());

            MainPageController.out.writeUTF(MainPageController.adresse_utilisateur);
            MainPageController.out.writeUTF(MainPageController.adresse_recepteur);
            MainPageController.out.writeUTF(MainPageController.nomutilisateur);
            MainPageController.out.writeUTF("audio");
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Erreur");
            alert.setContentText("L'appel n'a pas pu démarrer");
            alert.show();
            return;
        }

        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1Appel.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 196, 330);
        scene.getStylesheets().add(getClass().getResource("Page1Appel.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    void Envoie(ActionEvent event) {
        if (MainPageController.adresse_recepteur.equals("")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Personne trouvée");
            alert.setContentText("Choisir une personne");
            alert.show();
            return;
        }

        if (message_envoyer.getText().equals("")) return;

        Integer sender_id = 0, recever_id = 0;

        try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet1 = stmt.executeQuery("select connected_userid from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\") ;");
            while (resultSet1.next()) {
                sender_id = resultSet1.getInt(1);
            }
            resultSet1.close();

            ResultSet resultSet2 = stmt.executeQuery("select connected_userid from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \""+ MainPageController.recepteur +"\") ;");
            while (resultSet2.next()) {
                recever_id = resultSet2.getInt(1);
            }
            resultSet2.close();

            if (MainPageController.enligne.equals("offline")) {
                stmt.executeUpdate("insert into message(sender_id, recever_id, content)\n" +
                        "values (\""+ sender_id +"\", \""+ recever_id +"\", \""+ message_envoyer.getText() +"\");");
            } else {
                try {
                    MainPageController.out.writeUTF(MainPageController.adresse_utilisateur);
                    MainPageController.out.writeUTF(MainPageController.adresse_recepteur);
                    MainPageController.out.writeUTF(MainPageController.nomutilisateur);
                    MainPageController.out.writeUTF("message_fichier");
                    stmt.executeUpdate("insert into message(sender_id, recever_id, content)\n" +
                            "values (\"" + sender_id + "\", \"" + recever_id + "\", \"" + message_envoyer.getText() + "\");");
                } catch (IOException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Echec d'envoi");
                    alert.setContentText("Nous n'avons pas pu envoyer le message.");
                    alert.showAndWait();
                    return;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec d'envoi");
            alert.setContentText("Nous n'avons pas pu envoyer le message.");
            alert.showAndWait();
            return;
        }

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String dateformat = date.format(format);
        dateformat = "le " + dateformat;
        Label label1 = new Label();
        label1.setText(message_envoyer.getText());
        label1.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
        label1.setPadding(new Insets(5, 8, 7, 8));
        Label label2 = new Label();
        label2.setText(dateformat);
        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
        label2.setPadding(new Insets(0, 8, 5, 8));
        VBox vBox = new VBox();
        vBox.getChildren().add(label1);
        vBox.getChildren().add(label2);
        vBox.setAlignment(Pos.CENTER_RIGHT);
        vBox.setStyle("-fx-background-color : \"#67fd30\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
        HBox hBox = new HBox();
        vbox2.getChildren().add(hBox);
        hBox.getChildren().add(vBox);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));
        message_envoyer.deleteText(0, message_envoyer.getText().length());
    }

    @FXML
    void Recevoir(){
        while (true) {
            String adresse_recepteur, nom_recepteur, type_envoie;
            Integer self = 0, other = 0;

            try {
                adresse_recepteur = MainPageController.in.readUTF();
                nom_recepteur = MainPageController.in.readUTF();
                type_envoie = MainPageController.in.readUTF();
            } catch (IOException e) {
                break;
            }

            if (type_envoie.equals("message_fichier")) {
                if ((!MainPageController.adresse_recepteur.equals(adresse_recepteur))) {
                    Platform.runLater(() -> {
                        Label label = new Label();
                        label.setText(nom_recepteur + " vous a envoyé un nouveau message.");
                        label.setPadding(new Insets(40, 0, 40, 0));
                        label.setStyle("-fx-text-fill : \"white\"; -fx-font-family : \"Cambria Math\"; -fx-font-size : 14;");
                        messagePopup = new Popup();
                        HBox messagePane = new HBox();
                        messagePane.setStyle("-fx-background-color : \"lightblue\"; -fx-background-radius : 10; ");
                        messagePane.setAlignment(Pos.CENTER);
                        messagePane.setPrefHeight(10);
                        messagePane.setPadding(new Insets(0, 10, 0, 10));
                        messagePane.getChildren().add(label);
                        messagePopup.getContent().add(messagePane);
                        messagePopup.setAutoHide(true);
                        double x = hbox1.localToScreen(0, 0).getX() + hbox1.getWidth() / 2 + messagePane.getWidth() / 2;
                        double y = hbox1.localToScreen(0, 0).getY() + hbox1.getHeight();
                        messagePopup.show(hbox1, x, y);
                    });
                } else {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
                        List<Object> liste = Select.elements(MainPageController.recepteur);
                        self = (Integer) liste.get(1);
                        other = (Integer) liste.get(2);
                        ResultSet resultSet = stmt.executeQuery("select sender_id, recever_id, content, date_format(time_send, 'le %d/%m/%Y %H:%i'), content_image from message\n" +
                                "where sender_id in (" + self + ", " + other + ")\n" +
                                "and recever_id in (" + self + ", " + other + ") order by time_send desc limit 1;");
                        if (resultSet.next()) {
                            String message = resultSet.getString(3), date = resultSet.getString(4);
                            if (self.equals(resultSet.getInt(1))) {
                                if (resultSet.getBinaryStream(5) == null) {
                                    Platform.runLater(() -> {
                                        Label label1 = new Label();
                                        label1.setText(message);
                                        label1.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                                        label1.setPadding(new Insets(5, 8, 7, 8));
                                        Label label2 = new Label();
                                        label2.setText(date);
                                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                                        label2.setPadding(new Insets(0, 8, 5, 8));
                                        VBox vBox = new VBox();
                                        vBox.getChildren().add(label1);
                                        vBox.getChildren().add(label2);
                                        vBox.setAlignment(Pos.CENTER_RIGHT);
                                        vBox.setStyle("-fx-background-color : \"#67fd30\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                                        HBox hBox = new HBox();
                                        vbox2.getChildren().add(hBox);
                                        hBox.getChildren().add(vBox);
                                        hBox.setAlignment(Pos.CENTER_RIGHT);
                                        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));
                                    });

                                } else {
                                    Platform.runLater(() -> {
                                        Button button = new Button();
                                        button.setText(message);
                                        button.setMnemonicParsing(false);
                                        button.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                                        button.setPadding(new Insets(5, 8, 7, 8));
                                        button.setOnAction(e -> ouvrir(e));
                                        ImageView imageView = new ImageView(new Image(getClass().getResource("images/dossier.png").toString()));
                                        imageView.setFitWidth(28);
                                        imageView.setFitHeight(19);
                                        imageView.setPreserveRatio(true);
                                        imageView.setPickOnBounds(true);
                                        button.setGraphic(imageView);
                                        Label label2 = new Label();
                                        label2.setText(date);
                                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                                        label2.setPadding(new Insets(0, 8, 5, 8));
                                        VBox vBox = new VBox();
                                        vBox.getChildren().add(button);
                                        vBox.getChildren().add(label2);
                                        vBox.setAlignment(Pos.CENTER_RIGHT);
                                        vBox.setStyle("-fx-background-color : \"#67fd30\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                                        HBox hBox = new HBox();
                                        vbox2.getChildren().add(hBox);
                                        hBox.getChildren().add(vBox);
                                        hBox.setAlignment(Pos.CENTER_RIGHT);
                                        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));
                                    });
                                }
                            } else {
                                if (resultSet.getBinaryStream(5) == null) {
                                    Platform.runLater(() -> {
                                        Label label1 = new Label();
                                        label1.setText(message);
                                        label1.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                                        label1.setPadding(new Insets(5, 8, 7, 8));
                                        Label label2 = new Label();
                                        label2.setText(date);
                                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                                        label2.setPadding(new Insets(0, 8, 5, 8));
                                        VBox vBox = new VBox();
                                        vBox.getChildren().add(label1);
                                        vBox.getChildren().add(label2);
                                        vBox.setAlignment(Pos.CENTER_RIGHT);
                                        vBox.setStyle("-fx-background-color : \"#e7961c\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                                        HBox hBox = new HBox();
                                        ImageView imageView1 = new ImageView(new Image(getClass().getResource("images/cercle0.png").toString()));
                                        imageView1.setFitWidth(40);
                                        imageView1.setFitHeight(18);
                                        imageView1.setPreserveRatio(true);
                                        imageView1.setPickOnBounds(true);
                                        vbox2.getChildren().add(hBox);
                                        hBox.getChildren().add(imageView1);
                                        hBox.getChildren().add(vBox);
                                        HBox.setMargin(imageView1, new Insets(30, 0, 0, 0));
                                        HBox.setMargin(vBox, new Insets(0, 0, 0, 5));
                                    });

                                } else {
                                    Platform.runLater(() -> {
                                        Button button = new Button();
                                        button.setText(message);
                                        button.setMnemonicParsing(false);
                                        button.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                                        button.setPadding(new Insets(5, 8, 7, 8));
                                        button.setOnAction(e -> ouvrir(e));
                                        ImageView imageView = new ImageView(new Image(getClass().getResource("images/dossier.png").toString()));
                                        imageView.setFitWidth(28);
                                        imageView.setFitHeight(19);
                                        imageView.setPreserveRatio(true);
                                        imageView.setPickOnBounds(true);
                                        button.setGraphic(imageView);
                                        Label label2 = new Label();
                                        label2.setText(date);
                                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                                        label2.setPadding(new Insets(0, 8, 5, 8));
                                        VBox vBox = new VBox();
                                        vBox.getChildren().add(button);
                                        vBox.getChildren().add(label2);
                                        vBox.setAlignment(Pos.CENTER_RIGHT);
                                        vBox.setStyle("-fx-background-color : \"#e7961c\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                                        HBox hBox = new HBox();
                                        ImageView imageView1 = new ImageView(new Image(getClass().getResource("images/cercle0.png").toString()));
                                        imageView1.setFitWidth(40);
                                        imageView1.setFitHeight(18);
                                        imageView1.setPreserveRatio(true);
                                        imageView1.setPickOnBounds(true);
                                        vbox2.getChildren().add(hBox);
                                        hBox.getChildren().add(imageView1);
                                        hBox.getChildren().add(vBox);
                                        HBox.setMargin(imageView1, new Insets(30, 0, 0, 0));
                                        HBox.setMargin(vBox, new Insets(0, 0, 0, 5));
                                    });
                                }
                            }
                        }
                        resultSet.close();
                    } catch (SQLException e) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setHeaderText("Echec de chargement");
                            alert.setContentText("Nous n'arrivons pas à charger un message reçu.");
                            alert.show();
                        });
                    }
                }
            } else if (type_envoie.equals("audio")) {
                MainPageController.recepteur_audio = nom_recepteur;
                MainPageController.adressre_recepteur_audio = adresse_recepteur;
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setHeaderText(nom_recepteur+ " vous appelle");
                    alert.setContentText("Appuyer sur OK pour décrocher");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        try {
                            socket_audio = new Socket(MainPageController.ADRESSE_SERVEUR, ServeurAudio.NP_PORT);
                            in_audio = new DataInputStream(socket_audio.getInputStream());
                            out_audio = new DataOutputStream(socket_audio.getOutputStream());

                            FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1Appel.fxml"));
                            Scene scene = new Scene(fxmlLoader.load(), 196, 330);
                            scene.getStylesheets().add(getClass().getResource("Page1Appel.css").toExternalForm());
                            Stage stage = new Stage();
                            stage.setTitle("MonApp");
                            stage.setScene(scene);
                            stage.show();
                        } catch (IOException e) {
                            Alert alert1 = new Alert(Alert.AlertType.INFORMATION);
                            alert1.setHeaderText("Erreur");
                            alert1.setContentText("L'appel n'a pas pu démarrer");
                            alert1.show();
                        }
                    }
                });
            } else {
                MainPageController.recepteur_audio = nom_recepteur;
                MainPageController.adressre_recepteur_audio = adresse_recepteur;
                MainPageController.recepteur_video = nom_recepteur;
                MainPageController.adresse_recepteur_video = adresse_recepteur;
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setHeaderText(nom_recepteur+ " vous appelle");
                    alert.setContentText("Appuyer sur OK pour décrocher");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        try {
                            socket_video = new Socket(MainPageController.ADRESSE_SERVEUR, ServeurVideo.NP_PORT);
                            in_video = new DataInputStream(socket_video.getInputStream());
                            out_video = new DataOutputStream(socket_video.getOutputStream());

                            socket_audio = new Socket(MainPageController.ADRESSE_SERVEUR, ServeurAudio.NP_PORT);
                            in_audio = new DataInputStream(socket_audio.getInputStream());
                            out_audio = new DataOutputStream(socket_audio.getOutputStream());

                            FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1Video.fxml"));
                            Scene scene = new Scene(fxmlLoader.load(), 930, 410);
                            scene.getStylesheets().add(getClass().getResource("Page1VideoUI.css").toExternalForm());
                            Stage stage = new Stage();
                            stage.setTitle("MonApp");
                            stage.setScene(scene);
                            stage.show();
                        } catch (IOException e) {
                            Alert alert1 = new Alert(Alert.AlertType.INFORMATION);
                            alert1.setHeaderText("Erreur");
                            alert1.setContentText("L'appel n'a pas pu démarrer");
                            alert1.show();
                        }
                    }
                });
            }
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
    void Fichier()  {
        if (MainPageController.adresse_recepteur.equals("")) {
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
            if (fichier.length() > 16777215) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Trop volumineux");
                alert.setContentText("Envoyer des fichiers de moins de 50Mo");
                alert.showAndWait();
                return;
            }

            Integer sender_id = 0, recever_id = 0;

            try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement(); PreparedStatement ps = connection.prepareStatement("insert into message(sender_id, recever_id, content, content_image) values (?, ?, ?, ?);"); FileInputStream fichier_envoie = new FileInputStream(fichier);) {
                ResultSet resultSet1 = stmt.executeQuery("select connected_userid from connected_user\n" +
                        "where user_id in \n" +
                        "(select user_id from user where username = \"" + MainPageController.nomutilisateur + "\") ;");
                while (resultSet1.next()) {
                    sender_id = resultSet1.getInt(1);
                }
                resultSet1.close();

                ResultSet resultSet2 = stmt.executeQuery("select connected_userid from connected_user\n" +
                        "where user_id in \n" +
                        "(select user_id from user where username = \"" + MainPageController.recepteur + "\") ;");
                while (resultSet2.next()) {
                    recever_id = resultSet2.getInt(1);
                }
                resultSet2.close();

                if (MainPageController.enligne.equals("offline")) {
                    ps.setInt(1, sender_id);
                    ps.setInt(2, recever_id);
                    ps.setString(3, fichier.getName());
                    ps.setBinaryStream(4, fichier_envoie, fichier.length());
                    ps.executeUpdate();
                } else {
                    MainPageController.out.writeUTF(MainPageController.adresse_utilisateur);
                    MainPageController.out.writeUTF(MainPageController.adresse_recepteur);
                    MainPageController.out.writeUTF(MainPageController.nomutilisateur);
                    MainPageController.out.writeUTF("message_fichier");
                    ps.setInt(1, sender_id);
                    ps.setInt(2, recever_id);
                    ps.setString(3, fichier.getName());
                    ps.setBinaryStream(4, fichier_envoie, fichier.length());
                    ps.executeUpdate();
                }

            } catch (SQLException | IOException e) {
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Echec d'envoi");
                alert.setContentText("Nous n'avons pas pu envoyer le fichier.");
                alert.show();
                return;
            }

            LocalDateTime date = LocalDateTime.now();
            DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String dateformat = date.format(format);
            dateformat = "le " + dateformat;
            Button button = new Button();
            button.setText(fichier.getName());
            button.setMnemonicParsing(false);
            button.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
            button.setPadding(new Insets(5, 8, 7, 8));
            button.setOnAction(e -> ouvrir(e));
            ImageView imageView = new ImageView(new Image(getClass().getResource("images/dossier.png").toString()));
            imageView.setFitWidth(28);
            imageView.setFitHeight(19);
            imageView.setPreserveRatio(true);
            imageView.setPickOnBounds(true);
            button.setGraphic(imageView);
            Label label2 = new Label();
            label2.setText(dateformat);
            label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
            label2.setPadding(new Insets(0, 8, 5, 8));
            VBox vBox = new VBox();
            vBox.getChildren().add(button);
            vBox.getChildren().add(label2);
            vBox.setAlignment(Pos.CENTER_RIGHT);
            vBox.setStyle("-fx-background-color : \"#67fd30\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
            HBox hBox = new HBox();
            vbox2.getChildren().add(hBox);
            hBox.getChildren().add(vBox);
            hBox.setAlignment(Pos.CENTER_RIGHT);
            HBox.setMargin(vBox, new Insets(5, 0, 0, 0));
        } else {}
    }

    @FXML
    void ouvrir(ActionEvent event) {
        Button button_clique = (Button) event.getSource();

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select content_image from message\n" +
                    "where content = \""+ button_clique.getText() +"\" and content_image is not null;");

            if (resultSet.next()) {
                InputStream input = resultSet.getBinaryStream(1);
                InputStream input1 = resultSet.getBinaryStream(1);

                Path fichiertemp = Files.createTempFile("temp_", button_clique.getText());
                Files.copy(input, fichiertemp, StandardCopyOption.REPLACE_EXISTING);
                Desktop.getDesktop().open(fichiertemp.toFile());

                File fichier = new File("./telechargement/" + button_clique.getText());
                int numero = 1;
                while (fichier.exists()) {
                    fichier = new File("./telechargement/" + "(" + numero + ") " + button_clique.getText());
                    numero++;
                }

                OutputStream output = new FileOutputStream(fichier);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input1.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }

                output.close();
                input.close();
                input1.close();
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec d'ouverture");
            alert.setContentText("Nous n'arrivons pas à ouvrir le fichier.");
            alert.show();
        }
    }

    @FXML
    void Video() throws IOException {
        MainPageController.adressre_recepteur_audio = MainPageController.adresse_recepteur;
        MainPageController.recepteur_audio = MainPageController.recepteur;
        MainPageController.adresse_recepteur_video = MainPageController.adresse_recepteur;
        MainPageController.recepteur_video = MainPageController.recepteur;

        if (MainPageController.enligne.equals("offline")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Pas possible d'effectuer l'appel");
            alert.setContentText("L'utilisateur n'est pas en ligne");
            alert.show();
            return;
        }

        try {
            socket_video = new Socket(MainPageController.ADRESSE_SERVEUR, ServeurVideo.NP_PORT);
            in_video = new DataInputStream(socket_video.getInputStream());
            out_video = new DataOutputStream(socket_video.getOutputStream());

            socket_audio = new Socket(MainPageController.ADRESSE_SERVEUR, ServeurAudio.NP_PORT);
            in_audio = new DataInputStream(socket_audio.getInputStream());
            out_audio = new DataOutputStream(socket_audio.getOutputStream());

            MainPageController.out.writeUTF(MainPageController.adresse_utilisateur);
            MainPageController.out.writeUTF(MainPageController.adresse_recepteur);
            MainPageController.out.writeUTF(MainPageController.nomutilisateur);
            MainPageController.out.writeUTF("video");
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Erreur");
            alert.setContentText("L'appel n'a pas pu démarrer");
            alert.show();
            return;
        }

        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1Video.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 930, 410);
        scene.getStylesheets().add(getClass().getResource("Page1VideoUI.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    void Charger(ActionEvent event) {
        vbox2.getChildren().clear();
        button_appel.setDisable(false); button_fichier.setDisable(false); button_envoyer.setDisable(false); button_video.setDisable(false); button_info.setDisable(false); button_retirer.setDisable(false); button_emoji.setDisable(false);
        Button button_clique = (Button) event.getSource();

        String heure_derniere_connexion = "";
        MainPageController.recepteur = button_clique.getText();
        nom_utilisateur.setText(MainPageController.recepteur);
        Integer self = 0, other = 0;

        try {
            List<Object> liste = Select.elements(MainPageController.recepteur);
            MainPageController.enligne = (String) liste.get(0);
            self = (Integer) liste.get(1);
            other = (Integer) liste.get(2);
            MainPageController.adresse_recepteur = (String) liste.get(3);

        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec de chargement");
            alert.setContentText("Nous n'avons pas pu vous charger votre conversation, l'app risque de ne pas fonctionner correctement.");
            alert.show();
            return;
        }

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select sender_id, recever_id, content, date_format(time_send, 'le %d/%m/%Y %H:%i'), content_image from message\n" +
                    "where sender_id in ("+ self +", "+ other +")\n" +
                    "and recever_id in ("+ self +", "+ other +") order by time_send asc;");
            while (resultSet.next()) {
                if (self.equals(resultSet.getInt(1))) {
                    if (resultSet.getBinaryStream(5) == (null)) {
                        Label label1 = new Label();
                        label1.setText(resultSet.getString(3));
                        label1.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                        label1.setPadding(new Insets(5, 8, 7, 8));
                        Label label2 = new Label();
                        label2.setText(resultSet.getString(4));
                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                        label2.setPadding(new Insets(0, 8, 5, 8));
                        VBox vBox = new VBox();
                        vBox.getChildren().add(label1);
                        vBox.getChildren().add(label2);
                        vBox.setAlignment(Pos.CENTER_RIGHT);
                        vBox.setStyle("-fx-background-color : \"#67fd30\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                        HBox hBox = new HBox();
                        vbox2.getChildren().add(hBox);
                        hBox.getChildren().add(vBox);
                        hBox.setAlignment(Pos.CENTER_RIGHT);
                        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));
                    } else {
                        Button button = new Button();
                        button.setText(resultSet.getString(3));
                        button.setMnemonicParsing(false);
                        button.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                        button.setPadding(new Insets(5, 8, 7, 8));
                        button.setOnAction(e -> ouvrir(e));
                        ImageView imageView = new ImageView(new Image(getClass().getResource("images/dossier.png").toString()));
                        imageView.setFitWidth(28);
                        imageView.setFitHeight(19);
                        imageView.setPreserveRatio(true);
                        imageView.setPickOnBounds(true);
                        button.setGraphic(imageView);
                        Label label2 = new Label();
                        label2.setText(resultSet.getString(4));
                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                        label2.setPadding(new Insets(0, 8, 5, 8));
                        VBox vBox = new VBox();
                        vBox.getChildren().add(button);
                        vBox.getChildren().add(label2);
                        vBox.setAlignment(Pos.CENTER_RIGHT);
                        vBox.setStyle("-fx-background-color : \"#67fd30\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                        HBox hBox = new HBox();
                        vbox2.getChildren().add(hBox);
                        hBox.getChildren().add(vBox);
                        hBox.setAlignment(Pos.CENTER_RIGHT);
                        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));
                    }
                } else {
                    if (resultSet.getBinaryStream(5) == (null)) {
                        Label label1 = new Label();
                        label1.setText(resultSet.getString(3));
                        label1.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                        label1.setPadding(new Insets(5, 8, 7, 8));
                        Label label2 = new Label();
                        label2.setText(resultSet.getString(4));
                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                        label2.setPadding(new Insets(0, 8, 5, 8));
                        VBox vBox = new VBox();
                        vBox.getChildren().add(label1);
                        vBox.getChildren().add(label2);
                        vBox.setAlignment(Pos.CENTER_RIGHT);
                        vBox.setStyle("-fx-background-color : \"#e7961c\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                        HBox hBox = new HBox();
                        ImageView imageView = new ImageView(new Image(getClass().getResource("images/cercle0.png").toString()));
                        imageView.setFitWidth(40);
                        imageView.setFitHeight(18);
                        imageView.setPreserveRatio(true);
                        imageView.setPickOnBounds(true);
                        vbox2.getChildren().add(hBox);
                        hBox.getChildren().add(imageView);
                        hBox.getChildren().add(vBox);
                        HBox.setMargin(imageView, new Insets(30, 0, 0, 0));
                        HBox.setMargin(vBox, new Insets(0, 0, 0, 5));
                    } else {
                        Button button = new Button();
                        button.setText(resultSet.getString(3));
                        button.setMnemonicParsing(false);
                        button.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 14;");
                        button.setPadding(new Insets(5, 8, 7, 8));
                        button.setOnAction(e -> ouvrir(e));
                        ImageView imageView = new ImageView(new Image(getClass().getResource("images/dossier.png").toString()));
                        imageView.setFitWidth(28);
                        imageView.setFitHeight(19);
                        imageView.setPreserveRatio(true);
                        imageView.setPickOnBounds(true);
                        button.setGraphic(imageView);
                        Label label2 = new Label();
                        label2.setText(resultSet.getString(4));
                        label2.setStyle("-fx-font-family : \"Cambria Math\"; -fx-text-fill : black; -fx-font-size : 10;");
                        label2.setPadding(new Insets(0, 8, 5, 8));
                        VBox vBox = new VBox();
                        vBox.getChildren().add(button);
                        vBox.getChildren().add(label2);
                        vBox.setAlignment(Pos.CENTER_RIGHT);
                        vBox.setStyle("-fx-background-color : \"#e7961c\"; -fx-background-radius : 10; -fx-border-color : \"white\"; -fx-border-radius : 8; -fx-border-width : 3;");
                        HBox hBox = new HBox();
                        ImageView imageView1 = new ImageView(new Image(getClass().getResource("images/cercle0.png").toString()));
                        imageView1.setFitWidth(40);
                        imageView1.setFitHeight(18);
                        imageView1.setPreserveRatio(true);
                        imageView1.setPickOnBounds(true);
                        vbox2.getChildren().add(hBox);
                        hBox.getChildren().add(imageView1);
                        hBox.getChildren().add(vBox);
                        HBox.setMargin(imageView1, new Insets(30, 0, 0, 0));
                        HBox.setMargin(vBox, new Insets(0, 0, 0, 5));
                    }
                }
            }
            resultSet.close();

            if (MainPageController.enligne.equals("offline")) {
                profil_enligne.setImage(new Image(getClass().getResource("images/rondrouge.png").toString()));

                stmt.executeUpdate("set lc_time_names = 'fr_FR'; ");
                ResultSet resultSet2 = stmt.executeQuery("select date_format(last_connection, '%d/%m/%Y à %H:%i')\n" +
                        "from connected_user\n" +
                        "where user_id in\n" +
                        "(select user_id from user where username = \""+ MainPageController.recepteur +"\");");

                while (resultSet2.next()) {
                    heure_derniere_connexion = resultSet2.getString(1);
                }
                resultSet2.close();
                enligne.setText("En ligne : " + heure_derniere_connexion);
            } else {
                enligne.setText("Actuellement en ligne");
                profil_enligne.setImage(new Image(getClass().getResource("images/rondvert.png").toString()));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec de chargement");
            alert.setContentText("Nous n'avons pas pu vous charger votre conversation, l'app risque de ne pas fonctionner correctement.");
            alert.showAndWait();
        }

    }

    @FXML
    void Rechercher() {
        vbox1.getChildren().clear();
        int numero = 0;

        try (Connection  connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select user.username from\n" +
                    "contact join user on user.user_id = contact.contact_user_id\n" +
                    "where contact.user_id in\n" +
                    "(select user_id from user\n" +
                    "where username = \""+ MainPageController.nomutilisateur +"\") and\n" +
                    "user.username like \"%"+ recherche_conversation.getText().toLowerCase() +"%\";");
            while (resultSet.next()) {
                Button button = new Button(resultSet.getString(1));
                button.setPrefSize(191, 47);
                button.setGraphicTextGap(20);
                ImageView imageView = new ImageView(new Image(getClass().getResource("images/cercle"+ numero +".png").toString()));
                imageView.setFitWidth(36);
                imageView.setFitHeight(47);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
                button.setStyle("-fx-text-fill : white; -fx-border-color : gray; -fx-border-width : 0 0 0.1 0; -fx-cursor : hand;");
                button.setOnAction(event -> Charger(event));
                vbox1.getChildren().add(button);

                if (numero == 0) numero++;
                else numero--;
            }
            resultSet.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @FXML
    void ajouter(ActionEvent event) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("recherche.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 300, 400);
        scene.getStylesheets().add(getClass().getResource("recherche.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    void information(ActionEvent event) throws SQLException {
        Integer numero = 0;
        try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select phone_number from user\n" +
                    "where username = \""+ MainPageController.recepteur +"\";");
            while (resultSet.next()) {
                numero = resultSet.getInt(1);
            }
            resultSet.close();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Information");
            alert.setContentText("Nom : " + MainPageController.recepteur + "\n" + "Numéro : " + numero);
            alert.show();
        }
    }

    @FXML
    void retirer(ActionEvent event) {
        Integer id1 = 0, id2 = 0;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText("Voulez vous supprimer " + MainPageController.recepteur + " ?");
        alert.setContentText("Appuyer sur OK pour accpeter et Annuler sinon");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection connection = BaseDeDonnee.seConnecter(); Statement statement = connection.createStatement()) {
                ResultSet resultSet1 = statement.executeQuery("select user_id from user\n" +
                        "where username = \"" + MainPageController.nomutilisateur + "\";");
                while (resultSet1.next()) {
                    id1 = resultSet1.getInt(1);
                }
                resultSet1.close();

                ResultSet resultSet2 = statement.executeQuery("select user_id from user\n" +
                        "where username = \"" + MainPageController.recepteur + "\";");
                while (resultSet2.next()) {
                    id2 = resultSet2.getInt(1);
                }
                resultSet2.close();

                statement.executeUpdate("delete from contact\n" +
                        "where (user_id = " + id1 + " and contact_user_id = " + id2 + ") or \n" +
                        "(user_id = " + id2 + " and contact_user_id = " + id1 + ");");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    void emoji(ActionEvent event) {
        if (!(emojiPopup.isShowing())) {
            double x = button_emoji.localToScreen(0, 0).getX() - 2 * button_emoji.getWidth();
            double y = button_emoji.localToScreen(0, 0).getY() + button_emoji.getHeight();
            emojiPopup.show(button_emoji, x, y);
        } else {
            emojiPopup.hide();
        }
    }

}
