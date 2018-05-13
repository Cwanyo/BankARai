package com.thewiz.bankarai.tfmodels;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Trace;
import android.util.Log;

import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;

/**
 * Created by C.wan_yo on 18-Feb-18.
 */

public class TensorFlowImageClassifier implements Classifier {

    private static final String TAG = "TensorFlowImageClassifier";

    // Config return output.
    private int MAX_RESULTS;
    private float THRESHOLD;

    // Config values.
    private String inputName;
    private String outputName;
    private int inputSize;
    private int imageMean;
    private float imageStd;

    // Pre-allocated buffers.
    private Vector<String> labels = new Vector<String>();
    private int[] intValues;
    private float[] floatValues;
    private float[] outputs;
    private String[] outputNames;

    private boolean logStats = false;

    private TensorFlowInferenceInterface inferenceInterface;

    private TensorFlowImageClassifier() {

    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFileName The filepath of the model GraphDef protocol buffer.
     * @param labelFileName The filepath of label file for classes.
     * @param inputSize     The input size. A square image of inputSize x inputSize is assumed.
     * @param imageMean     The assumed mean of the image values.
     * @param imageStd      The assumed std of the image values.
     * @param inputName     The label of the image input node.
     * @param outputName    The label of the output node.
     * @throws IOException
     */
    public static Classifier create(
            AssetManager assetManager,
            String modelFileName,
            String labelFileName,
            int inputSize,
            int imageMean,
            float imageStd,
            String inputName,
            String outputName,
            int maxResult,
            float threshold
    ) {
        TensorFlowImageClassifier c = new TensorFlowImageClassifier();
        c.inputName = inputName;
        c.outputName = outputName;
        c.MAX_RESULTS = maxResult;
        c.THRESHOLD = threshold;

        // Read the label names into memory.
        String actualFileName = labelFileName.split("file:///android_asset/")[1];
        Log.i(TAG, "Reading labels from: " + actualFileName);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(actualFileName)));
            String line;
            while ((line = br.readLine()) != null) {
                c.labels.add(line);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem reading label file!", e);
        }

        c.inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFileName);

        // The shape of the output is [N, NUM_CLASSES], where N is the batch size.
        final Operation operation = c.inferenceInterface.graphOperation(outputName);
        final int numClasses = (int) operation.output(0).shape().size(1);
        Log.i(TAG, "Read " + c.labels.size() + " labels, output layer size is " + numClasses);

        // Ideally, inputSize could have been retrieved from the shape of the input operation.  Alas,
        // the placeholder node for input in the graphdef typically used does not specify a shape, so it
        // must be passed in as a parameter.
        c.inputSize = inputSize;
        c.imageMean = imageMean;
        c.imageStd = imageStd;

        // Pre-allocate buffers.
        c.outputNames = new String[]{outputName};
        c.intValues = new int[inputSize * inputSize];
        c.floatValues = new float[inputSize * inputSize * 3];
        c.outputs = new float[numClasses];

        return c;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {

        Trace.beginSection("recognizeImage");

        Trace.beginSection("preprocessBitmap");
        // Preprocess bitmap image from 0-255 int to float based.
        bitmap.getPixels(this.intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < this.intValues.length; ++i) {
            final int val = this.intValues[i];
            this.floatValues[i * 3 + 0] = ((val >> 16) & 0xFF) * 1.0f / 255.0f;
            this.floatValues[i * 3 + 1] = ((val >> 8) & 0xFF) * 1.0f / 255.0f;
            this.floatValues[i * 3 + 2] = (val & 0xFF) * 1.0f / 255.0f;
        }
        Trace.endSection();

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");
        this.inferenceInterface.feed(this.inputName, this.floatValues, 1, this.inputSize, this.inputSize, 3);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("run");
        this.inferenceInterface.run(this.outputNames, this.logStats);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("fetch");
        this.inferenceInterface.fetch(this.outputName, this.outputs);
        Trace.endSection();

        PriorityQueue<Recognition> pq = new PriorityQueue<Recognition>(
                3,
                new Comparator<Recognition>() {
                    @Override
                    public int compare(Recognition lhs, Recognition rhs) {
                        // Intentionally reversed to put high confidence at the head of the queue.
                        return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                    }
                }
        );

        // If output more then threshold add it to the queue.
        for (int i = 0; i < this.outputs.length; i++) {
            if (this.outputs[i] > this.THRESHOLD) {
                pq.add(new Recognition("" + i, this.labels.size() > i ? this.labels.get(i) : "unknown", this.outputs[i], null));
            }
        }

        // Get N number of max result from the queue.
        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        int recognitionsSize = Math.min(pq.size(), this.MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }

        Trace.endSection();

        return recognitions;
    }

    @Override
    public void enableStatLogging(boolean logStats) {
        this.logStats = logStats;
    }

    @Override
    public String getStatString() {
        return this.inferenceInterface.getStatString();
    }

    @Override
    public void close() {
        this.inferenceInterface.close();
    }
}
