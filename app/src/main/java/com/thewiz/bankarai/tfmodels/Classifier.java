package com.thewiz.bankarai.tfmodels;

import android.graphics.Bitmap;

import java.util.List;

/**
 * Created by C.wan_yo on 18-Feb-18.
 */

public interface Classifier {

    class Recognition {

        private final String id;
        private final String title;
        private final Float confidence;

        public Recognition(String id, String title, Float confidence) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
        }

        public String getId() {
            return this.id;
        }

        public String getTitle() {
            return this.title;
        }

        public Float getConfidence() {
            return this.confidence;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + this.id + "] ";
            }

            if (title != null) {
                resultString += this.title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", this.confidence * 100.0f);
            }

            return resultString.trim();
        }
    }

    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean logStats);

    String getStatString();

    void close();

}
