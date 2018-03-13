package com.thewiz.bankarai.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

/**
 * Created by C.wan_yo on 25-Feb-18.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener {

    private final String TAG = "TextSpeaker";

    String language;
    TextToSpeech tts;
    Context context = null;

    public TextSpeaker(Context context, String language) {
        this.context = context;
        this.language = language;
        this.tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts != null) {
                int result = tts.setLanguage(new Locale(language));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this.context, "TTS language is notsupported", Toast.LENGTH_LONG).show();
                    Log.e(TAG,"TTS language is notsupported");
                }
            }
        } else {
            Toast.makeText(this.context, "TTS initialization failed", Toast.LENGTH_LONG).show();
            Log.e(TAG,"TTS initialization failed");
        }
    }

    public void speakText(String text, int mode) {
        try{
            if (mode == 1) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null);
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        }catch (Exception e){
            Log.e(TAG,"Error:speakText()",e);
        }
    }

    public void stopSpeak() {
        try{
            tts.stop();
        }catch (Exception e){
            Log.e(TAG,"Error:stopSpeak()",e);
        }
    }

    public void close() {
        tts.stop();
        tts.shutdown();
    }
}
