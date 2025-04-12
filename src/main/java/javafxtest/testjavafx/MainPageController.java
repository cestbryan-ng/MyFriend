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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;

public class MainPageController {
    private static final String ADRESSE_SERVEUR = "";
    static Socket socket;
    static DataOutputStream out;
    static DataInputStream in;
    static boolean verificateur_vide = true;
    static Map<String, String> utilisateurs_etatsenligne = new HashMap<>();
    static List<String> liste_nom = new ArrayList<>();
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
    void Connexion(ActionEvent event) throws SQLException {
        nomutilisateur = nom_utilisateur.getText();
        String motdepasse = mot_de_passe_utilisateur.getText();
        message_erreur.setStyle("-fx-text-fill : red");

        if ((nomutilisateur.isEmpty()) || (motdepasse.isEmpty())) {
            message_erreur.setText("Entrer votre nom ou/et votre mot de passe");
            return;
        }

        try(Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/monapp", "Jean_Roland", "Papasenegal0");
            Statement stm = connection.createStatement()) {
            ResultSet resultSet1 = stm.executeQuery("select * from utilisateurs;");
            if (verificateur_vide) {
                while(resultSet1.next()) {
                    liste_nom.add(resultSet1.getString(2));
                    utilisateurs_etatsenligne.put(resultSet1.getString(2), resultSet1.getString(4));
                }
                verificateur_vide = false;
            }
            resultSet1.close();
            ResultSet resultSet = stm.executeQuery("select * from utilisateurs;");
            while (resultSet.next()) {
                if ((nomutilisateur.equals(resultSet.getString(2))) && (motdepasse.equals(resultSet.getString(3)))) {
                    stm.executeUpdate("update utilisateurs \n" +
                            "set indice_de_connexion = \"oui\" \n" +
                            "where nom_utilisateur = \""+ nomutilisateur +"\";");
                    liste_nom.remove(nomutilisateur);
                    utilisateurs_etatsenligne.put(nomutilisateur, "oui");
                    message_erreur.setText("Connexion réussie");
                    message_erreur.setStyle("-fx-text-fill : green");

                    // Connexion Serveur
                    try {
                        socket = new Socket(ADRESSE_SERVEUR, Serveur.NP_PORT);
                        in = new DataInputStream(socket.getInputStream());
                        out = new DataOutputStream(socket.getOutputStream());
                        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1UI.fxml"));
                        Scene scene = new Scene(fxmlLoader.load(), 950, 600);
                        scene.getStylesheets().add(getClass().getResource("Page1UI.css").toExternalForm());
                        Stage stage = new Stage();
                        stage.setTitle("MonApp");
                        stage.setScene(scene);
                        stage.show();
                        Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
                        stage1.close();
                        resultSet.close();
                    } catch (IOException e) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setHeaderText("Echec connexion");
                        alert.setContentText("Reesayer ultérieurement");
                        alert.showAndWait();
                    }

                    break;

                }
                message_erreur.setText("Utilisateur introuvable");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void Inscrire(ActionEvent event) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("MainPage1UI.fxml"));
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