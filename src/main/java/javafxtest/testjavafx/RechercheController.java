package javafxtest.testjavafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.ResourceBundle;

public class RechercheController implements Initializable {
    @FXML
    private AnchorPane anchorpane1;

    @FXML
    private Button contact;

    @FXML
    private TextField recherche_conversation;

    @FXML
    private Button rechercher;

    @FXML
    private VBox vbox1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        vbox1.getChildren().clear();
    }

    @FXML
    void rechercher() {
        vbox1.getChildren().clear();
        int numero = 0;

        try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("select username, phone_number from user\n" +
                    "where username != \""+ MainPageController.nomutilisateur +"\"\n" +
                    "and phone_number like \"%"+ recherche_conversation.getText().toLowerCase() +"%\"\n" +
                    "and user_id not in \n" +
                    "(select contact_user_id from\n" +
                    "contact join user on user.user_id = contact.user_id\n" +
                    "where user.username = \""+ MainPageController.nomutilisateur +"\");");

            while (resultSet.next()) {
                Button button = new Button(resultSet.getString(1) + "-" + resultSet.getInt(2));
                button.setPrefSize(191, 47);
                button.setGraphicTextGap(20);
                ImageView imageView = new ImageView(new Image(getClass().getResource("images/cercle"+ numero +".png").toString()));
                imageView.setFitWidth(36);
                imageView.setFitHeight(47);
                imageView.setPreserveRatio(true);
                button.setGraphic(imageView);
                button.setStyle("-fx-text-fill : white; -fx-border-color : gray; -fx-border-width : 1 0 1 0; -fx-cursor : hand; -fx-background-color : transparent;");
                button.setOnAction(event -> ajouter(event));
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
            alert.setContentText("Nous n'avons pas pu charger les contacts, redémarrer l'app.");
            alert.showAndWait();
        }
    }

    @FXML
    void ajouter(ActionEvent event) {
        Button button_clique = (Button) event.getSource();
        String nom = button_clique.getText().split("-")[0];
        Integer id1 = 0, id2 = 0;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText("Voulez vous ajouter " + nom + " ?");
        alert.setContentText("Appuyer sur OK pour accpeter et Annuler sinon");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection connection = BaseDeDonnee.seConnecter(); Statement statement = connection.createStatement()) {
                ResultSet resultSet1 = statement.executeQuery("select user_id from user\n" +
                        "where username = \""+ MainPageController.nomutilisateur +"\";");
                while (resultSet1.next()) {
                    id1 = resultSet1.getInt(1);
                }
                resultSet1.close();

                ResultSet resultSet2 = statement.executeQuery("select user_id from user\n" +
                        "where username = \""+ nom +"\";");
                while (resultSet2.next()) {
                    id2 = resultSet2.getInt(1);
                }
                resultSet2.close();

                statement.executeUpdate("insert into contact(user_id, contact_user_id)\n" +
                        "values ("+ id1 +", "+ id2 +"),\n" +
                        "("+ id2 +", "+ id1 +");");

                Stage stage1 = (Stage) anchorpane1.getScene().getWindow();
                stage1.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
