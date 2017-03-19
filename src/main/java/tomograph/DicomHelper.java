package tomograph;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class DicomHelper {
    void saveFile(Image image, String fileName, Properties properties) {
        File jpg = saveImage(image, fileName);
        if (jpg == null)
            throw new RuntimeException("Null jpg file");
        String path = "dicomfiles" + File.separator + fileName + ".dcm";
        File dcm = new File(path);
        try {
            dcm.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Jpg2Dcm jpg2Dcm = new Jpg2Dcm();
        try {
            jpg2Dcm.convert(jpg, dcm, properties);
        } catch (IOException e) {
            e.printStackTrace();
        }
        jpg.delete();

    }

    private File createFile(String name, String fileType) {
        if (fileType.matches("/.*")) throw new RuntimeException("wrong file type");
        File f = new File(name + fileType);
        if (f.exists()) {
            f.delete();
        }
        return f;
    }

    private static final int[] RGB_MASKS = {0xFF0000, 0xFF00, 0xFF};
    private static final ColorModel RGB_OPAQUE =
            new DirectColorModel(32, RGB_MASKS[0], RGB_MASKS[1], RGB_MASKS[2]);

    public File saveImage(Image image, String fileName) {
        try {

            BufferedImage bi = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.drawImage(SwingFXUtils.fromFXImage(image, null), 0, 0, (int) image.getWidth(), (int) image.getHeight(), null);
            g.dispose();
            File outputfile = new File(fileName + ".jpg");
            ImageIO.write(bi, "jpg", outputfile);
            return outputfile;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Couldn't create jpg file");
        }
    }
}
