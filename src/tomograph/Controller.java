package tomograph;

import javafx.event.ActionEvent;
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
import org.jtransforms.fft.FloatFFT_1D;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.ResourceBundle;

import static java.lang.Math.PI;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class Controller implements Initializable {


    public Canvas inputCanvas;
    public Canvas outputCanvas;
    public Canvas tomographyCanvas;
    public Slider alfaSlider, betaSlider, lSlider;
    public Label alfaValue, betaValue, lValue, filterValue;
    public Canvas inputImage, plot;
    public Canvas fft, filteredCanvas, ifft;
    private double alfa, l, filter;
    private int beta;
    private GraphicsContext gc;
    private Image image;
    private double r;

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
        alfaSlider.setMax(PI);
        lSlider.setMax(PI);
        alfaSlider.setMin(Math.toRadians(0.5));
        alfaSlider.setMax(Math.toRadians(45));
        alfaSlider.setValue(Math.toRadians(3));
        betaSlider.setValue(85);
        lSlider.setValue(Math.toRadians(175));
        gc = inputCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        image = new Image(this.getClass().getResource("TwoCircles.png").toString(), inputCanvas.getWidth(), inputCanvas.getHeight(), true, false);
        //image = new Image(this.getClass().getResource("SheppLoganPhantom.png").toString(), inputCanvas.getWidth(), inputCanvas.getHeight(), true, false);
        image = new Image(this.getClass().getResource("fantom.png").toString());
        //image = new Image("https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/SheppLogan_Phantom.svg/512px-SheppLogan_Phantom.svg.png");
        // ("http://img.medscapestatic.com/pi/meds/ckb/39/15039tn.jpg");
        GraphicsContext inputGraphicsContext = inputImage.getGraphicsContext2D();
        inputGraphicsContext.clearRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        inputGraphicsContext.setFill(Color.BLACK);
        inputGraphicsContext.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        inputGraphicsContext.drawImage(image, 0, 0);

    }

    public void draw(ActionEvent actionEvent) {
        gc.clearRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());

        inputImage.getGraphicsContext2D().drawImage(image, 0, 0);
        GraphicsContext outputGC = outputCanvas.getGraphicsContext2D();
        outputGC.setFill(Color.BLACK);
        outputGC.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());

        PixelReader pr = image.getPixelReader();
        Color c = pr.getColor(100, 100);
        System.out.println(c.getHue() + " " + c.getOpacity() + " " + c.getBrightness());
        gc.drawImage(image, 0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        r = Math.min(image.getWidth(), image.getHeight());
        r -= 2;
        r /= 2;
        double canvasR = (Math.min(inputCanvas.getWidth(), inputCanvas.getHeight()) - 2) / 2.f;

        gc.setStroke(Color.RED);
        gc.strokeOval(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        WritableImage radonTranform = new WritableImage(beta, (int) ((2 * PI) / alfa + 1));
        PixelWriter radonTransWritter = radonTranform.getPixelWriter();
        int x = 0, y = 0;
        for (float angle = 0; angle < 2.f * PI; angle += alfa) {
            for (double rayAngle = angle + PI - l / 2; rayAngle <= angle + PI + l / 2; rayAngle += l / (beta - 1)) {
                gc.strokeLine(canvasR + canvasR * Math.cos(angle), canvasR + canvasR * Math.sin(angle), canvasR + canvasR * Math.cos(rayAngle), canvasR + canvasR * Math.sin(rayAngle));
                double val = BresenhamLine((int) (r + r * Math.cos(angle)), (int) (r + r * Math.sin(angle)), (int) (r + r * Math.cos(rayAngle)), (int) (r + r * Math.sin(rayAngle)), pr);
                radonTransWritter.setColor(x++, y, Color.hsb(0, 0, val));
            }
            y++;
            x = 0;
        }
        normalize(radonTranform);
        tomographyCanvas.getGraphicsContext2D().drawImage(radonTranform, 0, 0, tomographyCanvas.getWidth(), tomographyCanvas.getHeight());
        //drawPlot
        PixelReader pr2 = radonTranform.getPixelReader();
        float[] plotData = new float[beta];
        for (int i = 0; i < beta; i++) {
            plotData[i] = (float) pr2.getColor(i, 0).getBrightness();
            plotData[i]=Math.max(0.5f,plotData[i]);
        }
        drawPlot(plot, plotData, beta);
        FloatFFT_1D floatFFT = new FloatFFT_1D(beta / 2);
        floatFFT.complexForward(plotData);
        for (int i = 0; i < beta / 2; i++)
            plotData[i] *= (float)i / beta;
        for (int i = beta / 2; i < beta; i++)
            plotData[i] *= 1f - (float)i / beta;
        drawPlot(fft, plotData, beta);
        floatFFT.complexInverse(plotData, false);
        drawPlot(filteredCanvas, plotData, beta);
        smoothData(plotData,beta);
        drawPlot(ifft, plotData, beta);
        //drawProjection
//        WritableImage outputImage = new WritableImage((int) image.getWidth(), (int) image.getHeight());
//        PixelWriter pixelWriter = outputImage.getPixelWriter();
//        PixelReader pixelReader = outputImage.getPixelReader();
        double[][] outputImage = new double[(int) image.getWidth()][(int) image.getHeight()];
        x = y = 0;
        for (float angle = 0; angle < 2.f * PI; angle += alfa) {
            float data[] = new float[beta];
            for(int i=0;i<beta;i++)
                data[i]=(float) pr2.getColor(i,y).getBrightness();
            filter(data,beta);
            smoothData(data,beta);
            x = 0;
            for (double rayAngle = angle + PI - l / 2; rayAngle <= angle + PI + l / 2; rayAngle += l / (beta-1)) {
                //double val = pr2.getColor(x, y).getBrightness();
                double val = data[x];
                x++;
                System.out.print(val + ", ");
                DrawBresenhamLine((int) (r + r * Math.cos(angle)), (int) (r + r * Math.sin(angle)), (int) (r + r * Math.cos(rayAngle)), (int) (r + r * Math.sin(rayAngle)), val, outputImage);
                //outputGC.setStroke(Color.hsb(0,0,val,0.5));
                //outputGC.strokeLine(canvasR + canvasR * Math.cos(angle), canvasR +canvasR * Math.sin(angle), canvasR + canvasR * Math.cos(rayAngle), canvasR + canvasR * Math.sin(rayAngle));

            }
            y++;
        }
        Image resultImage = normalizeAndMakeImage((int) image.getWidth(), (int) image.getHeight(), outputImage);
        outputGC.drawImage(resultImage, 0, 0, outputCanvas.getWidth(), outputCanvas.getHeight());
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

    private Image normalizeAndMakeImage(int width, int height, double[][] outputImage) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter pw = image.getPixelWriter();
        double min, max;
        min = max = outputImage[0][0];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double value = outputImage[x][y];
                min = Math.min(value, min);
                max = Math.max(value, max);
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double value = outputImage[x][y];
                value = (value - min) / (max - min);
                pw.setColor(x, y, Color.hsb(0, 0, value));
            }
        }
        return image;

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
        return output / (2 * r);
    }

    double DrawBresenhamLine(int x1, int y1, int x2, int y2, double value, double[][] outputImage) {
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
        mark(x, y, value, outputImage);

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
                mark(x, y, value, outputImage);
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
                mark(x, y, value, outputImage);
            }
        }
        return Math.min(output / (r * 2), 1);
    }

    private double getBrightness(int x, int y, PixelReader pr) {
        return pr.getColor(x, y).getBrightness();
    }

    private void mark(int x, int y, double val, double[][] outputImage) {
        val += outputImage[x][y];
        outputImage[x][y] = val;
    }

    private void drawPlot(Canvas canvas, float[] data, int size) {
        GraphicsContext gc2 = canvas.getGraphicsContext2D();
        gc2.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc2.beginPath();
        gc2.rect(0, 0, plot.getWidth(), plot.getHeight());
        gc2.moveTo(0, plot.getHeight());
        float max = 0;
        for (int i=0;i<size;i++)
            max=Math.max(max,data[i]);

        for (int i = 0; i < size; i++) {
            gc2.lineTo(i * plot.getWidth() / beta, plot.getHeight() * (1 - data[i]/max));
        }
        gc2.stroke();
    }
    private void filter(float[] data, int size){
        FloatFFT_1D floatFFT = new FloatFFT_1D(size / 2);
        floatFFT.complexForward(data);
        for (int i = 0; i < beta / 2; i++)
            data[i] *= (float)i / beta;
        for (int i = beta / 2; i < beta; i++)
            data[i] *= 1f - (float)i / beta;
        floatFFT.complexInverse(data, true);
    }
    private void smoothData(float[] data, int size){
        int windowSize=size/10;
        float sum;
        float[] output= data.clone();
        for(int i=0;i<size;i++){
            sum=0;
            for(int j=Math.max(0,i-windowSize/2);j<Math.min(i+windowSize/2,size);j++){
                sum+=output[j];
            }
            data[i]=sum/windowSize;
        }
    }
}
