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
import java.sql.*;
import java.util.*;

public class MainPage1Controller {

    @FXML
    private AnchorPane anchorpane1;

    @FXML
    private HBox hbox1;

    @FXML
    private Label message_erreur;

    @FXML
    private PasswordField mot_de_passe_utilisateur1;

    @FXML
    private PasswordField mot_de_passe_utilisateur2;

    @FXML
    private TextField nom_utilisateur;

    @FXML
    private TextField numero_tel;

    @FXML
    private TextField photo_profil;

    static List<String> liste_nom = new ArrayList<>();
    static List<String> liste_pass = new ArrayList<>();
    static Map<String, String> utilisateurs_etatsenligne = new HashMap<>();

    @FXML
    void profile(ActionEvent event) {

    }

    @FXML
    void Inscrire(ActionEvent event) {
        String nomutilisateur = nom_utilisateur.getText();
        String motdepasse1 = mot_de_passe_utilisateur1.getText();
        String motdepasse2 = mot_de_passe_utilisateur2.getText();
        String numero = numero_tel.getText();
        String profil = photo_profil.getText();
        liste_nom.clear();
        liste_pass.clear();
        message_erreur.setStyle("-fx-text-fill : red");

        if ((nomutilisateur.isEmpty()) || (motdepasse1.isEmpty()) || (motdepasse2.isEmpty()) || (numero.isEmpty())) {
            message_erreur.setText("Vérifier votre nom, mot de passe ou numéro");
            return;
        }

        if (!(motdepasse1.equals(motdepasse2))) {
            message_erreur.setText("Verifier votre mot de passe");
            return;
        }

        try(Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/monapp", "Jean_Roland", "Papasenegal0"); Statement stm = connection.createStatement()) {
            if (profil.isEmpty()) stm.executeUpdate("insert into user(username, code_acces, phone_number) values(\""+ nomutilisateur +"\", \""+ motdepasse1 +"\", "+ numero +");");
            else {}
            stm.executeUpdate("insert into connected_user(user_id)\n" +
                    "select user_id from user\n" +
                    "where username = \""+ nomutilisateur +"\";");
            message_erreur.setText("Inscription reussie");
            message_erreur.setStyle("-fx-text-fill : green");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Inscription reussie");
            alert.setContentText("Appuyez sur ok pour acceder à l'écran de connexion");
            alert.showAndWait();
            FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("MainPageUI.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 600, 250);
            scene.getStylesheets().add(getClass().getResource("MainPageUI.css").toExternalForm());
            Stage stage = new Stage();
            stage.setTitle("MonApp");
            stage.setScene(scene);
            stage.show();
            Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
            stage1.close();
        } catch (SQLException e) {
            message_erreur.setText("Utilisateur existant");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void Retour(ActionEvent event) throws IOException{
        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("MainPageUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 250);
        scene.getStylesheets().add(getClass().getResource("MainPageUI.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
        Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
        stage1.close();
    }
}