package tomograph;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import org.jtransforms.fft.FloatFFT_1D;

import java.io.File;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;

import static java.lang.Math.PI;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class Controller implements Initializable {


    public Canvas inputCanvas;
    public ImageView outputImageView;
    public Canvas tomographyCanvas;
    public Slider alfaSlider, betaSlider, lSlider, windowSlider;
    public Label alfaValue, betaValue, lValue, windowValue;
    public Canvas inputImage, plot;
    public Canvas fft, filteredCanvas, ifft;
    public RadioButton twoCirclePhantom, sheppLoganPhantom, squarePhantom, headPhantom, horsePhantom;
    public CheckBox drawLines;
    public TextField patientName;
    public TextArea comment;
    public TextField studyTime;
    public DatePicker patientBirthDate;
    public TextField patientSex;
    public DatePicker studyDate;
    public Label reconstructionErrorLabel;
    public Button drawButton;
    public ProgressBar progressBar;
    private double alfa, l;
    private int beta, windowSize;
    private GraphicsContext gc;
    private Image resultImage;
    private Image image;
    private double r;
    public TextField fileName;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        alfaSlider.valueProperty().addListener((event) -> {
            alfa = alfaSlider.getValue();
            alfaValue.setText(String.format("%.2f' = %.2f \u00B0", alfa, Math.toDegrees(alfa)));
        });
        betaSlider.valueProperty().addListener((event) -> {
            beta = (int) Math.round(betaSlider.getValue());
            if (beta % 2 == 0) beta += 1;
            betaValue.setText(String.format("%d", beta));
        });
        lSlider.valueProperty().addListener((event) -> {
            l = lSlider.getValue();
            lValue.setText(String.format("%.2f' = %.2f\u00B0", l, Math.toDegrees(l)));
        });
        windowSlider.valueProperty().addListener((event) -> {
            windowSize = (int) windowSlider.getValue();
            windowValue.setText(String.format("%d", windowSize));
        });
        alfaSlider.setMax(PI);
        lSlider.setMax(PI);
        alfaSlider.setMin(Math.toRadians(0.2));
        alfaSlider.setMax(Math.toRadians(45));
        alfaSlider.setValue(Math.toRadians(3));
        betaSlider.setMax(1000);
        betaSlider.setValue(85);
        lSlider.setMax(Math.toRadians(250));
        lSlider.setValue(Math.toRadians(180));
        windowSlider.setMin(1);
        windowSlider.setMax(20);
        windowSlider.setValue(1);
        gc = inputImage.getGraphicsContext2D();
        headPhantom.setSelected(true);
        loadImage();


    }

    public void draw(ActionEvent actionEvent) {
        drawButton.setDisable(true);
        progressBar.setVisible(true);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());

                inputImage.getGraphicsContext2D().drawImage(image, 0, 0);

                PixelReader pr = image.getPixelReader();
                gc.drawImage(image, 0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
                r = Math.min(image.getWidth(), image.getHeight());
                r -= 2;
                r /= 2;
                double canvasR = (Math.min(inputCanvas.getWidth(), inputCanvas.getHeight()) - 2) / 2.f;
                gc.setStroke(Color.RED);
                if (drawLines.isSelected())
                    gc.strokeOval(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
                WritableImage radonTranform = new WritableImage(beta, (int) ((2 * PI) / alfa + 1));
                PixelWriter radonTransWritter = radonTranform.getPixelWriter();
                int x = 0, y = 0;
                for (float angle = 0; angle < 2.f * PI; angle += alfa) {
                    for (double rayAngle = angle + PI - l / 2; rayAngle <= angle + PI + l / 2; rayAngle += l / (beta - 1)) {
                        if (drawLines.isSelected())
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
                }
                drawPlot(plot, plotData, beta);
                FloatFFT_1D floatFFT = new FloatFFT_1D(beta / 2);
                floatFFT.complexForward(plotData);
                for (int i = 0; i < beta / 2; i++)
                    plotData[i] *= (float) i / beta;
                for (int i = beta / 2; i < beta; i++)
                    plotData[i] *= 1f - (float) i / beta;
                drawPlot(fft, plotData, beta);
                floatFFT.complexInverse(plotData, false);
                drawPlot(filteredCanvas, plotData, beta);
                smoothData(plotData, beta);
                drawPlot(ifft, plotData, beta);
                double[][] outputImage = new double[(int) image.getWidth()][(int) image.getHeight()];
                double[][] outputNotFilteredImage = new double[(int) image.getWidth()][(int) image.getHeight()];
                y = 0;
                for (float angle = 0; angle < 2.f * PI; angle += alfa) {
                    float data[] = new float[beta];
                    for (int i = 0; i < beta; i++)
                        data[i] = (float) pr2.getColor(i, y).getBrightness();
                    filter(data, beta);
                    smoothData(data, beta);
                    x = 0;
                    for (double rayAngle = angle + PI - l / 2; rayAngle <= angle + PI + l / 2; rayAngle += l / (beta - 1)) {
                        double noFiltredData = pr2.getColor(x, y).getBrightness();
                        double filterdData = data[x];
                        x++;
                        DrawBresenhamLine((int) (r + r * Math.cos(angle)), (int) (r + r * Math.sin(angle)), (int) (r + r * Math.cos(rayAngle)), (int) (r + r * Math.sin(rayAngle)), filterdData, outputImage);
                        DrawBresenhamLine((int) (r + r * Math.cos(angle)), (int) (r + r * Math.sin(angle)), (int) (r + r * Math.cos(rayAngle)), (int) (r + r * Math.sin(rayAngle)), noFiltredData, outputNotFilteredImage);

                    }
                    y++;
                }
                resultImage = normalizeAndMakeImage((int) image.getWidth(), (int) image.getHeight(), outputImage);
                Image resultNoFiltredImage = normalizeAndMakeImage((int) image.getWidth(), (int) image.getHeight(), outputNotFilteredImage);

                Platform.runLater(() -> {
                    outputImageView.setImage(resultImage);
                    inputCanvas.getGraphicsContext2D().drawImage(resultNoFiltredImage, 0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
                    reconstructionErrorLabel.setText(String.format("%.2f", countError(image, resultImage)));
                });
                drawButton.setDisable(false);
                progressBar.setVisible(false);
            }
        });
        thread.start();
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

    private WritableImage normalizeAndMakeImage(int width, int height, double[][] outputImage) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter pw = image.getPixelWriter();
        double min, max;
        min = max = outputImage[width / 2][height / 2];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double value = outputImage[x][y];
                min = Math.min(value, min);
                max = Math.max(value, max);
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double value = (outputImage[x][y] - min) / (max - min);
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
        gc2.setStroke(Color.WHITE);
        gc2.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc2.beginPath();
        gc2.rect(0, 0, plot.getWidth(), plot.getHeight());
        float max, min;
        max = min = data[0];
        for (int i = 1; i < size; i++) {
            max = Math.max(max, data[i]);
            min = Math.min(max, data[i]);
        }
        gc2.moveTo(0, plot.getHeight());
        for (int i = 0; i < size; i++) {
            gc2.lineTo(i * plot.getWidth() / beta, plot.getHeight() * (1 - (data[i] - min) / (max - min)));
        }
        gc2.stroke();
    }

    private void filter(float[] data, int size) {
        FloatFFT_1D floatFFT = new FloatFFT_1D(size / 2);
        floatFFT.complexForward(data);
        for (int i = 0; i < beta / 2; i++)
            data[i] *= (float) i / beta;
        for (int i = beta / 2; i < beta; i++)
            data[i] *= 1f - (float) i / beta;
        floatFFT.complexInverse(data, true);
    }

    private void smoothData(float[] data, int size) {
        if (windowSize <= 1) return;
        float sum;
        float[] output = data.clone();
//        float average = 0;
//        for(int i=0;i<size;i++)
//            average+=data[i];
//        average/=size;
        for (int i = 0; i < size; i++) {
            sum = 0;
            for (int j = Math.max(0, i - windowSize / 2); j < Math.min(i + windowSize / 2, size); j++) {
                sum += output[j];
            }
            data[i] = sum / windowSize;
        }
    }

    @FXML
    private void loadImage() {
        gc.clearRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        String name = "";
        if (sheppLoganPhantom.isSelected())
            name = "SheppLoganPhantom.png";
        else if (twoCirclePhantom.isSelected())
            name = "TwoCircles.png";
        else if (squarePhantom.isSelected())
            name = "fantom.png";
        else if (headPhantom.isSelected())
            name = "head.png";
        else if (horsePhantom.isSelected())
            name = "horse.png";
        image = new Image(this.getClass().getResource("/images/" + name).toString(), inputCanvas.getWidth(), inputCanvas.getHeight(), true, false);
        GraphicsContext inputGraphicsContext = inputImage.getGraphicsContext2D();
        inputGraphicsContext.setFill(Color.BLACK);
        inputGraphicsContext.fillRect(0, 0, inputCanvas.getWidth(), inputCanvas.getHeight());
        inputGraphicsContext.drawImage(image, 0, 0);
        canvasToBlack(inputCanvas);
        canvasToBlack(tomographyCanvas);
        outputImageView.setImage(null);
        resultImage = null;
        canvasToBlack(plot);
        canvasToBlack(ifft);
        canvasToBlack(fft);
        canvasToBlack(filteredCanvas);
        reconstructionErrorLabel.setText("-");
    }

    private void canvasToBlack(Canvas canvas) {
        GraphicsContext graphicsContext2D = canvas.getGraphicsContext2D();
        graphicsContext2D.setFill(Color.BLACK);
        graphicsContext2D.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    public void saveImage(ActionEvent actionEvent) {
        if (resultImage == null)
            draw(null);
        if (fileName.getText().isEmpty())
            fileName.setText("dicom");
        DicomHelper dh = new DicomHelper();
        Properties properties = new Properties();
        properties.put("00100010", patientName.getText()); // Patien Full Name
        properties.put("00100040", patientSex.getText()); //Patient's Sex
        if (patientBirthDate.getValue() != null)
            properties.put("00100030", patientBirthDate.getValue().toString()); //Patient's Birth Date
        if (studyDate.getValue() != null)
            properties.put("00080020", studyDate.getValue().toString()); //Study Date
        properties.put("00080030", studyTime.getText()); //Study Time
        properties.put("00324000", comment.getText()); //Comments
        dh.saveFile(resultImage, fileName.getText(), properties);
    }

    public void saveJpg(ActionEvent actionEvent) {
        if (resultImage == null)
            draw(null);
        DicomHelper dicomHelper = new DicomHelper();
        dicomHelper.saveImage(image, fileName.getText());
    }

    public float countError(Image first, Image second) {
        PixelReader firstPR = first.getPixelReader();
        PixelReader secondPR = second.getPixelReader();
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight())
            throw new RuntimeException("Not equal sizes of compared images");
        float error = 0;
        for (int x = 0; x < first.getWidth(); x++)
            for (int y = 0; y < first.getHeight(); y++) {
                double firstCol = firstPR.getColor(x, y).getBrightness();
                double secondCol = secondPR.getColor(x, y).getBrightness();
                error += (firstCol - secondCol) * (firstCol - secondCol);
            }
        return error;
    }
}
