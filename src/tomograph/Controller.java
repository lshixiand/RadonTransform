package tomograph;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.ResourceBundle;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

public class Controller implements Initializable {


    public Canvas inputCanvas;
    public Canvas outputCanvas;
    public Canvas tomographyCanvas;
    public Canvas filterCanvas;
    public Slider alfaSlider, betaSlider, lSlider, filterSlider;
    public Label alfaValue, betaValue, lValue, filterValue;
    private double alfa, l, filter;
    private int beta;
    private GraphicsContext gc;
    private Image image;
    double r;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        alfaSlider.valueProperty().addListener((event) -> {
            alfa = alfaSlider.getValue();
            alfaValue.setText(String.format("%.2f' = %.2f \u00B0", alfa, Math.toDegrees(alfa)));
        });
        betaSlider.valueProperty().addListener((event) -> {
            beta = (int) Math.round(betaSlider.getValue());
            betaValue.setText(String.format("%d", beta));
        });
        lSlider.valueProperty().addListener((event) -> {
            l = lSlider.getValue();
            lValue.setText(String.format("%.2f' = %.2f\u00B0", l, Math.toDegrees(l)));
        });
        filterSlider.valueProperty().addListener((event) -> {
            filter = filterSlider.getValue();
            filterValue.setText(String.format("%.2f", filter));
        });
        alfaSlider.setMax(PI);
        lSlider.setMax(PI);
        filterSlider.setValue(0.3f);
        alfaSlider.setValue(Math.toRadians(30));
        betaSlider.setValue(5);
        lSlider.setValue(Math.toRadians(60));
        gc = inputCanvas.getGraphicsContext2D();


    }

    public void draw(ActionEvent actionEvent) {
        gc.clearRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        GraphicsContext outputGC = outputCanvas.getGraphicsContext2D();
        outputGC.setFill(Color.BLACK);
        outputGC.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        image = new Image(this.getClass().getResource("fantom.png").toString());
        image = new Image(this.getClass().getResource("SheppLoganPhantom.png").toString());
        //image = new Image("https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/SheppLogan_Phantom.svg/512px-SheppLogan_Phantom.svg.png");//("http://img.medscapestatic.com/pi/meds/ckb/39/15039tn.jpg");
        PixelReader pr = image.getPixelReader();
        Color c = pr.getColor(100, 100);
        System.out.println(c.getHue() + " " + c.getOpacity() + " " + c.getBrightness());
        Tomograph tomograph = new Tomograph(image);
        gc.drawImage(image, 0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        r = Math.min(image.getWidth(), image.getHeight());
        r -= 2;
        r /= 2;
        double canvasR = (Math.min(inputCanvas.getWidth(), inputCanvas.getHeight()) - 2) / 2.f;

        gc.setStroke(Color.RED);
        gc.strokeOval(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        WritableImage outputImage = new WritableImage((int) image.getWidth(), (int) image.getHeight());
        PixelWriter pixelWriter = outputImage.getPixelWriter();
        PixelReader pixelReader = outputImage.getPixelReader();
        WritableImage radonTranform = new WritableImage(beta, (int) ((2 * PI) / alfa + 1));
        PixelWriter radonTransWritter = radonTranform.getPixelWriter();
        int x = 0, y = 0;
        for (float angle = 0; angle < 2.f * PI; angle += alfa) {
            for (double rayAngle = angle + PI - l / 2; rayAngle <= angle + PI + l / 2; rayAngle += l / (beta - 1)) {

                gc.strokeLine(canvasR + canvasR * Math.cos(angle), canvasR + canvasR * Math.sin(angle), canvasR + canvasR * Math.cos(rayAngle), canvasR + canvasR * Math.sin(rayAngle));
                double val = BresenhamLine((int) (r + r * Math.cos(angle)), (int) (r + r * Math.sin(angle)), (int) (r + r * Math.cos(rayAngle)), (int) (r + r * Math.sin(rayAngle)), pr);
                radonTransWritter.setColor(x++, y, Color.hsb(0, 0, val));
                if (val < filter) continue;
                System.out.println(val);
                DrawBresenhamLine((int) (r + r * Math.cos(angle)), (int) (r + r * Math.sin(angle)), (int) (r + r * Math.cos(rayAngle)), (int) (r + r * Math.sin(rayAngle)), val, pixelReader, pixelWriter);
                //outputGC.setStroke(Color.hsb(0,0,val,0.1f));
                //outputGC.strokeLine(canvasR + canvasR * Math.cos(angle), canvasR +canvasR * Math.sin(angle), canvasR + canvasR * Math.cos(rayAngle), canvasR + canvasR * Math.sin(rayAngle));
            }
            y++;
            x = 0;
        }
        //normalize(radonTranform);
        tomographyCanvas.getGraphicsContext2D().drawImage(radonTranform, 0, 0, tomographyCanvas.getWidth(), tomographyCanvas.getHeight());
        outputGC.drawImage(outputImage, 0, 0, outputCanvas.getWidth(), outputCanvas.getHeight());
    }

    private void normalize(WritableImage radonTranform) {
        PixelReader pr = radonTranform.getPixelReader();
        double min, max;
        min = max = getBrightness(0, 0, pr);
        for (int x = 0; x < radonTranform.getWidth(); x++) {
            for (int y = 0; y < radonTranform.getHeight(); y++) {
                double value = getBrightness(x, y, pr);
                min = Math.min(value, min);
                max = Math.max(value, max);
            }
        }
        PixelWriter pw = radonTranform.getPixelWriter();

        for (int x = 0; x < radonTranform.getWidth(); x++) {
            for (int y = 0; y < radonTranform.getHeight(); y++) {
                double value = getBrightness(x, y, pr);
                value = (value - min) / (max - min);
                pw.setColor(x, y, Color.hsb(0, 0, value));
            }
        }


    }

    private double BresenhamLine(int x1, int y1, int x2, int y2, PixelReader pr) {
        int d, dx, dy, ai, bi, xi, yi;
        int x = x1, y = y1;
        double output = 0;
        if (x1 < x2) {
            xi = 1;
            dx = x2 - x1;
        } else {
            xi = -1;
            dx = x1 - x2;
        }
        if (y1 < y2) {
            yi = 1;
            dy = y2 - y1;
        } else {
            yi = -1;
            dy = y1 - y2;
        }
        output += getBrightness(x, y, pr);

        if (dx > dy) {
            ai = (dy - dx) * 2;
            bi = dy * 2;
            d = bi - dx;
            while (x != x2) {
                if (d >= 0) {
                    x += xi;
                    y += yi;
                    d += ai;
                } else {
                    d += bi;
                    x += xi;
                }
                output += getBrightness(x, y, pr);
            }
        } else {
            ai = (dx - dy) * 2;
            bi = dx * 2;
            d = bi - dy;
            while (y != y2) {
                if (d >= 0) {
                    x += xi;
                    y += yi;
                    d += ai;
                } else {
                    d += bi;
                    y += yi;
                }
                output += getBrightness(x, y, pr);
            }
        }
        return output/(2*r);
    }

    double DrawBresenhamLine(int x1, int y1, int x2, int y2, double value, PixelReader pr, PixelWriter pw) {
        int d, dx, dy, ai, bi, xi, yi;
        int x = x1, y = y1;
        double output = 0;
        if (x1 < x2) {
            xi = 1;
            dx = x2 - x1;
        } else {
            xi = -1;
            dx = x1 - x2;
        }
        if (y1 < y2) {
            yi = 1;
            dy = y2 - y1;
        } else {
            yi = -1;
            dy = y1 - y2;
        }
        mark(x, y, value, pr, pw);

        if (dx > dy) {
            ai = (dy - dx) * 2;
            bi = dy * 2;
            d = bi - dx;
            while (x != x2) {
                if (d >= 0) {
                    x += xi;
                    y += yi;
                    d += ai;
                } else {
                    d += bi;
                    x += xi;
                }
                mark(x, y, value, pr, pw);
            }
        } else {
            ai = (dx - dy) * 2;
            bi = dx * 2;
            d = bi - dy;
            while (y != y2) {
                if (d >= 0) {
                    x += xi;
                    y += yi;
                    d += ai;
                } else {
                    d += bi;
                    y += yi;
                }
                mark(x, y, value, pr, pw);
            }
        }
        return Math.min(output / (r * 2), 1);
    }

    private double getBrightness(int x, int y, PixelReader pr) {
        return pr.getColor(x, y).getBrightness();
    }

    private void mark(int x, int y, double val, PixelReader pr, PixelWriter pw) {
        val = (0.01 + pr.getColor(x, y).getBrightness());
        val = min(val,1);
        pw.setColor(x, y, Color.hsb(0, 0, val));
    }
}
