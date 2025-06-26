package javafxtest.testjavafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.net.Socket;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.net.Inet4Address;
import java.util.ResourceBundle;

public class MainPageController implements Initializable {
    static final String ADRESSE_SERVEUR = "a953a40d-d088-4fc2-9b65-d1965847c80e-00-8cpatqzy8vbh.spock.replit.dev";
    static Socket socket;
    static DataOutputStream out;
    static DataInputStream in;
    static String nomutilisateur = "";
    static String adresse_utilisateur = "";
    static String adresse_recepteur = "";
    static String adressre_recepteur_audio = "";
    static String adresse_recepteur_video = "";
    static String recepteur = "";
    static String recepteur_audio = "";
    static String recepteur_video = "";
    static String enligne = "";

    @FXML
    private PasswordField mot_de_passe_utilisateur;

    @FXML
    private TextField nom_utilisateur;

    @FXML
    private Label message_erreur;

    @FXML
    private AnchorPane anchorpane1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // AJOUTER CES LIGNES POUR LA TOUCHE ENTRÉE
        nom_utilisateur.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Connexion(new ActionEvent());
            }
        });

        mot_de_passe_utilisateur.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Connexion(new ActionEvent());
            }
        });
    }

    @FXML
    void Connexion(ActionEvent event) {
        nomutilisateur = nom_utilisateur.getText();
        String motdepasse = mot_de_passe_utilisateur.getText();
        message_erreur.setStyle("-fx-text-fill : red");

        if ((nomutilisateur.isEmpty()) || (motdepasse.isEmpty())) {
            message_erreur.setText("Entrer votre nom ou/et votre mot de passe");
            return;
        }

        try(Connection connection = BaseDeDonnee.seConnecter();
            Statement stm = connection.createStatement()) {
            ResultSet resultSet = stm.executeQuery("select * from user;");
            while (resultSet.next()) {
                if ((nomutilisateur.equals(resultSet.getString(2))) && (motdepasse.equals(resultSet.getString(3)))) {
                    resultSet.close();
                    stm.executeUpdate("update connected_user\n" +
                            "set statut = \"online\"\n" +
                            "where user_id in (\n" +
                            "select user_id from user where username = \""+ nomutilisateur +"\");");

                    // Connexion Serveur
                    socket = new Socket(ADRESSE_SERVEUR, Serveur.NP_PORT);
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());

                    adresse_utilisateur = Inet4Address.getLocalHost().toString();
                    adresse_utilisateur = "/" + adresse_utilisateur.split("/")[1];

                    stm.executeUpdate("update connected_user\n" +
                            "set adresse_ip = \""+ Inet4Address.getLocalHost().toString() +"\"\n" +
                            "where user_id in (\n" +
                            "select user_id from user where username = \""+ nomutilisateur +"\");");

                    // Lancement de la nouvelle fenetre
                    FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1UI.fxml"));
                    Scene scene = new Scene(fxmlLoader.load(), 950, 600);
                    scene.getStylesheets().add(getClass().getResource("Page1UI.css").toExternalForm());
                    Stage stage = new Stage();
                    stage.setTitle("MonApp");
                    stage.setScene(scene);
                    stage.setOnCloseRequest(e -> {
                        try {
                            MainPageController.in.close();
                            MainPageController.out.close();
                            MainPageController.socket.close();

                            Connection  connection2 = BaseDeDonnee.seConnecter();
                            Statement stmt = connection2.createStatement();
                            stmt.executeUpdate("update connected_user\n" +
                                    "set statut = \"offline\"\n" +
                                    "where user_id in (\n" +
                                    "select user_id from user where username = \""+ MainPageController.nomutilisateur +"\");");

                            stmt.executeUpdate("update connected_user\n" +
                                    "set last_connection = current_timestamp\n" +
                                    "where user_id in\n" +
                                    "(select user_id from user where username = \""+ MainPageController.nomutilisateur +"\");");

                            stmt.close();
                            connection2.close();
                        } catch (IOException | SQLException err) {
                        }
                    });
                    stage.setResizable(false);
                    stage.show();
                    Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
                    stage1.close();
                    return;
                }
            }

            message_erreur.setText("Utilisateur ou Mot de passe incorrect");

        } catch (SQLException | IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec de connexion");
            alert.setContentText("Nous n'avons pas pu vous connecter");
            alert.showAndWait();
        }
    }

    @FXML
    void Inscrire(ActionEvent event) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("MainPage1UI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 323);
        scene.getStylesheets().add(getClass().getResource("MainPage1UI.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
        stage1.close();
    }
}