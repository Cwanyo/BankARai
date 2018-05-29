package com.thewiz.bankarai;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
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
import android.view.MotionEvent;
import android.widget.Toast;

import com.thewiz.bankarai.cams.CameraActivity;
import com.thewiz.bankarai.tfmodels.Classifier;
import com.thewiz.bankarai.tfmodels.Classifier.Recognition;
import com.thewiz.bankarai.tfmodels.TensorFlowImageClassifier;
import com.thewiz.bankarai.tfmodels.TensorFlowObjectDetectionAPIModel;
import com.thewiz.bankarai.tracking.MultiBoxTracker;
import com.thewiz.bankarai.tts.TextSpeaker;
import com.thewiz.bankarai.utils.BorderedText;
import com.thewiz.bankarai.utils.ImageUtils;
import com.thewiz.bankarai.views.OverlayView;
import com.thewiz.bankarai.views.OverlayView.DrawCallback;
import com.thewiz.bankarai.views.ResultsView;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class MainActivity extends CameraActivity implements OnImageAvailableListener {

    private final static String TAG = "MainActivity";

    // Config Model
    // (Classifier)
    private static final int BINARY_INPUT_SIZE = 300;
    private static final int BINARY_IMAGE_MEAN = 300;
    private static final float BINARY_IMAGE_STD = 128.0f;
    private static final String BINARY_INPUT_NAME = "conv2d_1_input";
    private static final String BINARY_OUTPUT_NAME = "dense_3/Softmax";
    private static final int BINARY_MAX_RESULTS = 1;
    private static final float BINARY_THRESHOLD = 0.8f;

    // (Detector)
    private static final int TF_OD_INPUT_SIZE = 300;
    private static final int TF_OD_MAX_RESULTS = 1;
    private static final int TF_OD_NUM_CLASSES = 6;
    private static final float TF_OD_THRESHOLD = 0.8f;

    // Assets
    // (Classifier)
    private static final String BINARY_MODEL_FILE = "file:///android_asset/binary_classification_own.pb";
    private static final String BINARY_LABEL_FILE = "file:///android_asset/binary_classification.txt";

    // (Detector)
    private static final String TF_OD_API_MODEL_FILE = "file:///android_asset/banknotes_detection.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/banknotes_detection.txt";

    // TODO - check, = false
    private static final boolean MAINTAIN_ASPECT = false;

    // Config preview size
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    //private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);

    private Integer sensorOrientation;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;

    private long lastProcessingTimeMs;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    // TODO - Object tracker
//    private MultiBoxTracker tracker;

    private byte[] luminanceCopy;

    private OverlayView trackingOverlay;
    private ResultsView resultsView;
    private BorderedText borderedText;

    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        // Classifier
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

        // Detector
//        tracker = new MultiBoxTracker(this);
        int cropSize = TF_OD_INPUT_SIZE;
        try {
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(),
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_INPUT_SIZE,
                    TF_OD_MAX_RESULTS,
                    TF_OD_NUM_CLASSES,
                    TF_OD_THRESHOLD);

            cropSize = TF_OD_INPUT_SIZE;
        } catch (final IOException e) {
            Log.e(TAG, "Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        Log.i(TAG, String.format("Camera orientation relative to screen canvas: %d", sensorOrientation));

        Log.i(TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        resultsView = (ResultsView) findViewById(R.id.results);

//        trackingOverlay.addCallback(
//                new DrawCallback() {
//                    @Override
//                    public void drawCallback(final Canvas canvas) {
//                        tracker.draw(canvas);
//                        if (isDebug()) {
//                            tracker.drawDebug(canvas);
//                        }
//                    }
//                });

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderDebug(canvas);
                    }
                });
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
//        tracker.onFrame(
//                previewWidth,
//                previewHeight,
//                getLuminanceStride(),
//                sensorOrientation,
//                originalLuminance,
//                timestamp);
//        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
//        Log.i(TAG, "Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        readyForNextImage();

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
//                        Log.i(TAG,"Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();

                        if (pressed) {
                            // Binary classification
                            List<Classifier.Recognition> binaryResult = binaryClassifier.recognizeImage(croppedBitmap);

                            // If banknote in frame
                            if (binaryResult.size() > 0 && binaryResult.get(0).getTitle().equals("banknote")) {
                                // Show classifier result view
                                resultsView.setResults(binaryResult);
                                // Detector
                                // TODO - only one output
                                List<Recognition> detectionResult = detector.recognizeImage(croppedBitmap);
                                speakResult(detectionResult);
                                // Draw Rect of object
                                drawRect(detectionResult, currTimestamp);
                            } else {
                                // Remove classifier result view
                                Log.i(TAG, "nothing classified");
                                resultsView.setResults(null);
                                speakResult(null);
                            }
                        } else {
                            resultsView.setResults(null);
                            ts.stopSpeak();
                            preResult = "null";
                        }

                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        requestRender();
                        computingDetection = false;
                    }
                });
    }

    String preResult = "";

    private void speakResult(List<Recognition> results) {
        if (results == null || results.size() == 0
                || results.get(0).getTitle().equals("???")
                || results.get(0).getTitle().equals("unknown")) {
            Log.d(TAG, "banknote not in frame");
            if (!preResult.equals("null")) {
                ts.stopSpeak();
            }
            preResult = "null";
            // TODO - running voice
            ts.sayRunning();
        } else if (!(preResult.equals(results.get(0).getTitle()))) {
            Log.d(TAG, "new banknote detected");
            ts.stopSpeak();
            ts.speakText(results.get(0).getTitle(), 1);
            preResult = results.get(0).getTitle();
        } else if (preResult.equals(results.get(0).getTitle())) {
            Log.d(TAG, "same banknote detected");
            ts.speakText(results.get(0).getTitle(), 1);
            preResult = results.get(0).getTitle();
        }
    }

    private Bitmap resizeImageForClassifier(Bitmap input) {
        Bitmap newImage = Bitmap.createBitmap(BINARY_INPUT_SIZE, BINARY_INPUT_SIZE, Config.ARGB_8888);
        Matrix transformation = ImageUtils.getTransformationMatrix(
                input.getWidth(), input.getHeight(),
                BINARY_INPUT_SIZE, BINARY_INPUT_SIZE,
                0, true);
        transformation.invert(new Matrix());

        Canvas canvas = new Canvas(newImage);
        canvas.drawBitmap(input, transformation, null);

        return newImage;
    }

    private void drawRect(List<Recognition> results, long currTimestamp) {
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
        final Canvas canvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        final List<Recognition> mappedRecognitions = new LinkedList<Recognition>();

        for (final Recognition result : results) {
            final RectF location = result.getLocation();
            canvas.drawRect(location, paint);

            cropToFrameTransform.mapRect(location);
            result.setLocation(location);
            mappedRecognitions.add(result);
        }

        // TODO - Update tracker layout, Can be remove
//        tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
        trackingOverlay.postInvalidate();
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
        detector.enableStatLogging(debug);
    }

    private void renderDebug(final Canvas canvas) {
        if (!isDebug()) {
            return;
        }
        final Bitmap copy = cropCopyBitmap;
        if (copy == null) {
            return;
        }

        final int backgroundColor = Color.argb(100, 0, 0, 0);
        canvas.drawColor(backgroundColor);

        final Matrix matrix = new Matrix();
        final float scaleFactor = 2;
        matrix.postScale(scaleFactor, scaleFactor);
        matrix.postTranslate(
                canvas.getWidth() - copy.getWidth() * scaleFactor,
                canvas.getHeight() - copy.getHeight() * scaleFactor);
        canvas.drawBitmap(copy, matrix, new Paint());

        final Vector<String> lines = new Vector<String>();
        // TODO - display node infos
//        if (detector != null) {
//            final String statString = detector.getStatString();
//            final String[] statLines = statString.split("\n");
//            for (final String line : statLines) {
//                lines.add(line);
//            }
//        }
//        lines.add("");

        lines.add("Object: " + preResult);
        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

        borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
    }

}
