package com.thewiz.bankarai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;

import com.thewiz.bankarai.cams.CameraActivity;
import com.thewiz.bankarai.tfmodels.Classifier;
import com.thewiz.bankarai.tfmodels.Classifier.Recognition;
import com.thewiz.bankarai.tfmodels.TensorFlowImageClassifier;
import com.thewiz.bankarai.tts.TextSpeaker;
import com.thewiz.bankarai.utils.BorderedText;
import com.thewiz.bankarai.utils.ImageUtils;
import com.thewiz.bankarai.views.OverlayView.DrawCallback;
import com.thewiz.bankarai.views.ResultsView;

import java.util.List;
import java.util.Vector;

public class MainActivity extends CameraActivity implements OnImageAvailableListener {

    private final static String TAG = "MainActivity";

    // Config Model
    // Mobilenet - model
    /*private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "final_result";*/

    // Binary Banknotes - Using Inception
    private static final int BINARY_INPUT_SIZE = 299;
    private static final int BINARY_IMAGE_MEAN = 128;
    private static final float BINARY_IMAGE_STD = 128.0f;
    private static final String BINARY_INPUT_NAME = "Mul";
    private static final String BINARY_OUTPUT_NAME = "final_result";
    private static final int BINARY_MAX_RESULTS = 1;
    private static final float BINARY_THRESHOLD = 0.7f;

    // Thai Banknotes - Using Inception
    private static final int INPUT_SIZE = 299;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final String INPUT_NAME = "Mul";
    private static final String OUTPUT_NAME = "final_result";
    private static final int MAX_RESULTS = 1;
    private static final float THRESHOLD = 0.7f;

    // Assets
    // Binary Banknotes
    private static final String BINARY_MODEL_FILE = "file:///android_asset/binary_graph.pb";
    private static final String BINARY_LABEL_FILE = "file:///android_asset/binary_labels.txt";
    // Thai Banknotes
    private static final String MODEL_FILE = "file:///android_asset/th_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/th_labels.txt";

    private static final boolean SAVE_PREVIEW_BITMAP = false;

    private static final boolean MAINTAIN_ASPECT = true;

    // Config preview size
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    //private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);

    private Integer sensorOrientation;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private Bitmap cropCopyBitmap;

    private boolean computing = false;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private ResultsView resultsView;

    private BorderedText borderedText;

    private long lastProcessingTimeMs;

    private static final float TEXT_SIZE_DIP = 10;

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        // Binary Banknotes classifier - 2 classes
        binaryClassifier = TensorFlowImageClassifier.create(
                getAssets(),
                BINARY_MODEL_FILE,
                BINARY_LABEL_FILE,
                BINARY_INPUT_SIZE,
                BINARY_IMAGE_MEAN,
                BINARY_IMAGE_STD,
                BINARY_INPUT_NAME,
                BINARY_OUTPUT_NAME,
                BINARY_MAX_RESULTS,
                BINARY_THRESHOLD);

        // Thai Banknotes classifier - 5 classes
        classifier = TensorFlowImageClassifier.create(
                getAssets(),
                MODEL_FILE,
                LABEL_FILE,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME,
                MAX_RESULTS,
                THRESHOLD);

        resultsView = (ResultsView) findViewById(R.id.results);
        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();

        Log.i(TAG, String.format("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation));

        sensorOrientation = rotation + screenOrientation;

        Log.i(TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));
        rgbBytes = new int[previewWidth * previewHeight];
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        INPUT_SIZE, INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        yuvBytes = new byte[3][];

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderDebug(canvas);
                    }
                });
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = null;

        try {
            image = imageReader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }
            computing = true;

            Trace.beginSection("imageAvailable");

            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Recognition> binaryResults = binaryClassifier.recognizeImage(croppedBitmap);
                        List<Recognition> results = null;

                        // TODO - this process is slow, changing the binary model from inception to mobilenet might improve the performance
                        // Check if banknote in frame
                        if(banknoteInFrame(binaryResults)){
                            results = classifier.recognizeImage(croppedBitmap);
                            speakResult(results);
                        }

                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        resultsView.setResults(results);
                        requestRender();
                        computing = false;
                    }
                });

        Trace.endSection();
    }

    private Boolean banknoteInFrame(List<Recognition> results){
        if (results.size() == 0 || results.get(0).getTitle().equals("unknown")){
            Log.d(TAG,"banknote not in frame");
            ts.stopSpeak();
            return false;
        }else {
            Log.d(TAG,"banknote in frame");
            return true;
        }
    }

    String preObject = "";

    private void speakResult(List<Recognition> results){

        if (results.size() == 0){
            Log.d(TAG,"banknote in frame but can not be identified");
            ts.stopSpeak();
            return;
        }else if(!(preObject.equals(results.get(0).getTitle()))){
            Log.d(TAG,"new banknote detected");
            ts.stopSpeak();
            ts.speakText(results.get(0).getTitle(),1);
        }else{
            Log.d(TAG,"same banknote detected");
            ts.speakText(results.get(0).getTitle(),1);
        }

        preObject = results.get(0).getTitle();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_camera_connection;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onSetDebug(boolean debug) {
        classifier.enableStatLogging(debug);
    }

    private void renderDebug(final Canvas canvas) {
        if (!isDebug()) {
            return;
        }
        final Bitmap copy = cropCopyBitmap;
        if (copy != null) {
            final Matrix matrix = new Matrix();
            final float scaleFactor = 2;
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(
                    canvas.getWidth() - copy.getWidth() * scaleFactor,
                    canvas.getHeight() - copy.getHeight() * scaleFactor);
            canvas.drawBitmap(copy, matrix, new Paint());

            final Vector<String> lines = new Vector<String>();
            if (classifier != null) {
                String statString = classifier.getStatString();
                String[] statLines = statString.split("\n");
                for (String line : statLines) {
                    lines.add(line);
                }
            }

            lines.add("Frame: " + previewWidth + "x" + previewHeight);
            lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
            lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
            lines.add("Rotation: " + sensorOrientation);
            lines.add("Inference time: " + lastProcessingTimeMs + "ms");

            borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
        }
    }

}
