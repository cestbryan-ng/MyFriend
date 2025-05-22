package javafxtest.testjavafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.net.Socket;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.net.Inet4Address;

public class MainPageController {
    private static final String ADRESSE_SERVEUR = "192.168.6.1";
    static Socket socket;
    static DataOutputStream out;
    static DataInputStream in;
    static String nomutilisateur;

    @FXML
    private HBox hbox1;

    @FXML
    private PasswordField mot_de_passe_utilisateur;

    @FXML
    private TextField nom_utilisateur;

    @FXML
    private Label message_erreur;

    @FXML
    private AnchorPane anchorpane1;

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
                    stm.executeUpdate("update connected_user\n" +
                            "set adresse_ip = \""+ Inet4Address.getLocalHost().toString() +"\"\n" +
                            "where user_id in (\n" +
                            "select user_id from user where username = \""+ nomutilisateur +"\");");
                    FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1UI.fxml"));
                    Scene scene = new Scene(fxmlLoader.load(), 950, 600);
                    scene.getStylesheets().add(getClass().getResource("Page1UI.css").toExternalForm());
                    Stage stage = new Stage();
                    stage.setTitle("MonApp");
                    stage.setScene(scene);
                    stage.show();
                    Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
                    stage1.close();
                    break;
                }
            }
            message_erreur.setText("Utilisateur introuvable");

        } catch (SQLException | IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Echec de connexion");
            alert.setContentText("Réesayer ultérieurement");
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
        stage.show();
        Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
        stage1.close();
    }
}