package com.thewiz.bankarai.tfmodels;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

/**
 * Created by C.wan_yo on 18-Feb-18.
 */

public interface Classifier {

    class Recognition {

        private final String id;
        private final String title;
        private final Float confidence;

        private RectF location;

        public Recognition(String id, String title, Float confidence, RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
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

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
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

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean logStats);

    String getStatString();

    void close();

}
