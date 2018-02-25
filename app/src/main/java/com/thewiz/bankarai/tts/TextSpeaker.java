package com.thewiz.bankarai.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

/**
 * Created by C.wan_yo on 25-Feb-18.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener {

    TextToSpeech tts;
    Context context = null;

    public TextSpeaker(Context context) {
        this.context = context;
        this.tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts != null) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this.context, "TTS language is notsupported", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(this.context, "TTS initialization failed", Toast.LENGTH_LONG).show();
        }
    }

    public void speakText(String text, int mode) {
        if (mode == 1) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void stopSpeak() {
        tts.stop();
    }

    public void close() {
        tts.stop();
        tts.shutdown();
    }
}
