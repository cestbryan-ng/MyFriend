package javafxtest.testjavafx;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import java.io.IOException;
import java.net.URL;
import javafx.scene.image.Image;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;

public class Page1VideoController implements Initializable {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static VideoCapture videoCapture;
    private static boolean encours = true;

    @FXML
    private VBox vbox1;

    @FXML
    private ImageView imageview1;

    @FXML
    private ImageView imageview2;

    public void initialize(URL location, ResourceBundle resources) {
        new Thread(this::recevoir).start();

        String adresse_recepteur = "";

        try (Connection connection = BaseDeDonnee.seConnecter(); Statement stmt = connection.createStatement()) {
            ResultSet resultSet1 = stmt.executeQuery("select adresse_ip from connected_user\n" +
                    "where user_id in \n" +
                    "(select user_id from user where username = \"" + Page1Controller.recepteur + "\");");
            while (resultSet1.next()) {
                adresse_recepteur = resultSet1.getString(1);
                adresse_recepteur = "/" + adresse_recepteur.split("/")[1];
            }
            resultSet1.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            Page1Controller.out.writeUTF(adresse_recepteur);
            Page1Controller.out.writeUTF("video");
        } catch (IOException e) {
            e.printStackTrace();
        }

        videoCapture = new VideoCapture(0);
        encours = true;

        Mat trame = new Mat();

        Thread thread = new Thread(() -> {
            while (encours) {
                if (videoCapture.read(trame)) {
                    Image image = MatEnImage.matToImage(trame);

                    Platform.runLater(() -> {
                        imageview1.setImage(image);
                    });

                    MatOfByte buffer = new MatOfByte();
                    Imgcodecs.imencode(".jpg", trame, buffer);
                    byte[] data = buffer.toArray();

                    try {
                        Page1Controller.out.writeInt(data.length);
                        Page1Controller.out.write(data);
                        Page1Controller.out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep(33);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        thread.start();
    }

    @FXML
    void racrocher(ActionEvent event) throws IOException {
        encours = false;
        if (videoCapture.isOpened()) videoCapture.release();

        Page1Controller.in.close();
        Page1Controller.out.close();
        Page1Controller.socket.close();

        FXMLLoader fxmlLoader = new FXMLLoader(MainPage.class.getResource("Page1UI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 950, 600);
        scene.getStylesheets().add(getClass().getResource("Page1UI.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("MonApp");
        stage.setScene(scene);
        stage.show();
        Stage stage1 = (Stage) vbox1.getScene().getWindow();
        stage1.close();
    }

    @FXML
    void recevoir() {
        byte[] data = null;

        try {
            while (true) {
                int length = Page1Controller.in.readInt();
                data = new byte[length];
                Page1Controller.in.readFully(data);

                Mat img = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.IMREAD_COLOR);
                Image fxImage = MatEnImage.matToImage(img);

                Platform.runLater(() -> {
                    imageview2.setImage(fxImage);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
