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

import java.nio.ByteBuffer;
import java.util.ResourceBundle;

public class Page1VideoController implements Initializable {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static VideoCapture videoCapture;

    @FXML
    private VBox vbox1;

    @FXML
    private ImageView imageview1;

    @FXML
    private ImageView imageview2;

    public void initialize(URL location, ResourceBundle resources) {
//        new Thread(this::recevoir).start();

        videoCapture = new VideoCapture(0);

//        MatOfByte buffer = new MatOfByte();

        Mat trame = new Mat();

        Thread thread = new Thread(() -> {
            while (true) {
                if (videoCapture.read(trame)) {
                    Image image = MatEnImage.matToImage(trame);
                    Platform.runLater(() -> imageview1.setImage(image));

//                    Imgcodecs.imencode(".jpg", trame, buffer);
//                    byte[] data = buffer.toArray();
//
//                    try {
//                        MainPageController.out.writeUTF("/127.0.0.1");
//                        MainPageController.out.writeUTF("video");
//                        MainPageController.out.write(ByteBuffer.allocate(4).putInt(data.length).array());
//                        MainPageController.out.write(data);
//                        MainPageController.out.flush();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }

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
        videoCapture.release();

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
//        byte[] imageBytes = null;
//        while (true) {
//            try {
//                byte[] sizeBytes = MainPageController.in.readNBytes(4);
//                int size = ByteBuffer.wrap(sizeBytes).getInt();
//
//                imageBytes = MainPageController.in.readNBytes(size);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            Mat img = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
//
//            Image fxImage = MatEnImage.matToImage(img);
//            Platform.runLater(() -> imageview2.setImage(fxImage));
//        }
    }

}
