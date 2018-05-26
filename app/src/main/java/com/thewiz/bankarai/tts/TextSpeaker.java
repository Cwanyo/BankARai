package com.thewiz.bankarai.tts;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;

/**
 * Created by C.wan_yo on 25-Feb-18.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {

    private final String TAG = "TextSpeaker";

    public Boolean doneWelcome = false;

    int languageIndex = 0;
    String[] language = new String[]{"en", "th"};

    TextToSpeech tts;
    Context context = null;

    public TextSpeaker(Context context, int languageIndex) {
        this.languageIndex = languageIndex;
        this.context = context;
        this.tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts != null) {
                initTTS();
            }
        } else {
            Toast.makeText(this.context, "TTS initialization failed", Toast.LENGTH_LONG).show();
            Log.e(TAG, "TTS initialization failed");
        }
    }

    public void initTTS() {
        int result = tts.setLanguage(new Locale(language[languageIndex]));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this.context, "TTS language is notsupported", Toast.LENGTH_LONG).show();
            Log.e(TAG, "TTS language is notsupported");
        } else {
            doneWelcome = false;
            tts.setOnUtteranceCompletedListener(this);
            sayWelcome();
        }
    }

    public void switchTTSLanguage() {
        languageIndex++;
        if (languageIndex >= language.length) {
            languageIndex = 0;
        }
        Log.d(TAG, "CurrLagIndex: " + languageIndex);

        initTTS();
    }

    public void sayWelcome() {
        String welcome = "";
        switch (languageIndex) {
            case 0:
                welcome = "Welcome, please touch the screen to start detecting banknotes";
//                speakText("Welcome, please touch the screen to start detecting banknotes", 1);
                break;
            case 1:
                welcome = "ยินดีต้อนรับ, โปรดแตะหน้าจอเพื่อเริ่มตรวจธนบัตร";
                speakText("ยินดีต้อนรับ, โปรดแตะหน้าจอเพื่อเริ่มตรวจธนบัตร", 1);
                break;
        }

        try {
            HashMap<String, String> myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "WELCOME MESSAGE");
            tts.speak(welcome, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        } catch (Exception e) {
            Log.e(TAG, "Error:sayWelcome()", e);
        }
    }

    @Override
    public void onUtteranceCompleted(String utteranceId) {
        if (utteranceId.equals("WELCOME MESSAGE")) {
            doneWelcome = true;
        }
        Log.i(TAG,"Statue : "+doneWelcome);
    }

    public void sayRunning() {
        switch (languageIndex) {
            case 0:
                speakText("Detecting", 1);
                break;
            case 1:
                speakText("กำลังตรวจ", 1);
                break;
        }
    }

    public void speakText(String text, int mode) {
        if (!doneWelcome) {
            return;
        }

        try {
            if (mode == 1) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null);
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error:speakText()", e);
        }
    }

    public void stopSpeak() {
        if (!doneWelcome) {
            return;
        }

        try {
            tts.stop();
        } catch (Exception e) {
            Log.e(TAG, "Error:stopSpeak()", e);
        }
    }

    public void close() {
        tts.stop();
        tts.shutdown();
    }

}
