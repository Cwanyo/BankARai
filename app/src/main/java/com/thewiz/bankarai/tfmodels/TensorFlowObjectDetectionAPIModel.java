package com.thewiz.bankarai.tfmodels;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Log;

import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;

/**
 * Created by C.wan_yo on 02-May-18.
 */

public class TensorFlowObjectDetectionAPIModel implements Classifier {

    private static final String TAG = "TensorFlowObjectDetectionAPIModel";

    // Only return this many results.
    private int MAX_RESULTS;
    private float THRESHOLD;
    private int NUM_CLASSES;

    // Config values.
    private String inputName;
    private int inputSize;

    // Pre-allocated buffers.
    private Vector<String> labels = new Vector<String>();
    private int[] intValues;
    private byte[] byteValues;
    private float[] outputLocations;
    private float[] outputScores;
    private float[] outputClasses;
    private float[] outputNumDetections;
    private String[] outputNames;

    private boolean logStats = false;

    private TensorFlowInferenceInterface inferenceInterface;

    private TensorFlowObjectDetectionAPIModel() {

    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     */
    public static Classifier create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final int inputSize,
            final int maxResult,
            final int numClasses,
            final float threshold
    ) throws IOException {
        final TensorFlowObjectDetectionAPIModel d = new TensorFlowObjectDetectionAPIModel();

        InputStream labelsInput = null;
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        labelsInput = assetManager.open(actualFilename);
        BufferedReader br = null;
        br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            Log.w(TAG, line);
            d.labels.add(line);
        }
        br.close();


        d.inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);

        final Graph g = d.inferenceInterface.graph();

        d.inputName = "image_tensor";
        // The inputName node has a shape of [N, H, W, C], where
        // N is the batch size
        // H = W are the height and width
        // C is the number of channels (3 for our purposes - RGB)
        final Operation inputOp = g.operation(d.inputName);
        if (inputOp == null) {
            throw new RuntimeException("Failed to find input Node '" + d.inputName + "'");
        }
        d.inputSize = inputSize;

        d.MAX_RESULTS = maxResult;
        d.THRESHOLD = threshold;
        d.NUM_CLASSES = numClasses;

        // The outputScoresName node has a shape of [N, NumLocations], where N
        // is the batch size.
        final Operation outputOp1 = g.operation("detection_scores");
        if (outputOp1 == null) {
            throw new RuntimeException("Failed to find output Node 'detection_scores'");
        }
        final Operation outputOp2 = g.operation("detection_boxes");
        if (outputOp2 == null) {
            throw new RuntimeException("Failed to find output Node 'detection_boxes'");
        }
        final Operation outputOp3 = g.operation("detection_classes");
        if (outputOp3 == null) {
            throw new RuntimeException("Failed to find output Node 'detection_classes'");
        }

        // Pre-allocate buffers.
        d.outputNames = new String[]{"detection_boxes", "detection_scores",
                "detection_classes", "num_detections"};
        d.intValues = new int[d.inputSize * d.inputSize];
        d.byteValues = new byte[d.inputSize * d.inputSize * 3];

        // TODO - output number below should be number of output = 100
        d.outputScores = new float[d.NUM_CLASSES];
        d.outputLocations = new float[d.NUM_CLASSES * 4];
        d.outputClasses = new float[d.NUM_CLASSES];
        d.outputNumDetections = new float[1];
        return d;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("preprocessBitmap");
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < intValues.length; ++i) {
            byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);
            byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);
            byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);
        }
        Trace.endSection(); // preprocessBitmap

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");
        inferenceInterface.feed(inputName, byteValues, 1, inputSize, inputSize, 3);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("run");
        inferenceInterface.run(outputNames, logStats);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("fetch");
        outputLocations = new float[NUM_CLASSES * 4];
        outputScores = new float[NUM_CLASSES];
        outputClasses = new float[NUM_CLASSES];
        outputNumDetections = new float[1];
        inferenceInterface.fetch(outputNames[0], outputLocations);
        inferenceInterface.fetch(outputNames[1], outputScores);
        inferenceInterface.fetch(outputNames[2], outputClasses);
        inferenceInterface.fetch(outputNames[3], outputNumDetections);
        Trace.endSection();

        // Find the best detections.
        final PriorityQueue<Recognition> pq = new PriorityQueue<Recognition>(
                1,
                new Comparator<Recognition>() {
                    @Override
                    public int compare(Recognition lhs, Recognition rhs) {
                        // Intentionally reversed to put high confidence at the head of the queue.
                        return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                    }
                }
        );

        // Scale them back to the input size, If output more then threshold add it to the queue.
        for (int i = 0; i < outputScores.length; ++i) {
            if (this.outputScores[i] > this.THRESHOLD) {
                final RectF detection = new RectF(
                        outputLocations[4 * i + 1] * inputSize,
                        outputLocations[4 * i] * inputSize,
                        outputLocations[4 * i + 3] * inputSize,
                        outputLocations[4 * i + 2] * inputSize);
                pq.add(new Recognition("" + i, labels.get((int) outputClasses[i]), outputScores[i], detection));
            }
        }

        // Get N number of max result from the queue.
        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        int recognitionsSize = Math.min(pq.size(), this.MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }

        Trace.endSection(); // "recognizeImage"
        return recognitions;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {
        this.logStats = logStats;
    }

    @Override
    public String getStatString() {
        return inferenceInterface.getStatString();
    }

    @Override
    public void close() {
        inferenceInterface.close();
    }
}
