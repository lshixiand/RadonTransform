package tomograph;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

/**
 * Created by mruga on 28.02.2017.
 */
public class Tomograph {
    private Image image;

    public Tomograph(Image image) {
        this.image = image;
    }
    double color2Grey(Color c){ return c.getBrightness(); }

}
