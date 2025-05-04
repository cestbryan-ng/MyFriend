package javafxtest.testjavafx;

import javafx.scene.image.Image;
import org.opencv.core.Mat;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class MatEnImage {
    public static Image matToImage(Mat frame) {
        int width = frame.width();
        int height = frame.height();
        int channels = frame.channels();

        byte[] sourcePixels = new byte[width * height * channels];
        frame.get(0, 0, sourcePixels);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

        return SwingFXUtils.toFXImage(image, null);
    }
}
