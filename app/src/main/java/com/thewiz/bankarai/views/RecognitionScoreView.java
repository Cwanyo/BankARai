package com.thewiz.bankarai.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.thewiz.bankarai.R;
import com.thewiz.bankarai.tfmodels.Classifier.Recognition;

import java.util.List;

/**
 * Created by C.wan_yo on 23-Feb-18.
 */

public class RecognitionScoreView extends View implements ResultsView {

    private static final float TEXT_SIZE_DIP = 24;
    private List<Recognition> results;
    private final float textSizePx;
    private final Paint fgPaint;

    public RecognitionScoreView(final Context context, final AttributeSet set) {
        super(context, set);

        textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        fgPaint = new Paint();
        fgPaint.setTextSize(textSizePx);
        int color = ContextCompat.getColor(context, R.color.yellow);
        fgPaint.setColor(color);

        fgPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void setResults(final List<Recognition> results) {
        this.results = results;
        postInvalidate();
    }

    @Override
    public void onDraw(final Canvas canvas) {
        final int x = canvas.getWidth() / 2;

        int y = (int) (fgPaint.getTextSize() * 1.5f);

        if (results != null) {
            for (final Recognition recog : results) {
                canvas.drawText(recog.getTitle() + ": " + recog.getConfidence(), x, y, fgPaint);
                y += fgPaint.getTextSize() * 1.5f;
            }
        }
    }

}
