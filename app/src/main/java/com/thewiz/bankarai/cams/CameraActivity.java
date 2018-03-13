package com.thewiz.bankarai.cams;

import android.Manifest;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.media.Image;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.thewiz.bankarai.R;
import com.thewiz.bankarai.tfmodels.Classifier;
import com.thewiz.bankarai.tts.TextSpeaker;
import com.thewiz.bankarai.views.OverlayView;

import java.nio.ByteBuffer;

public abstract class CameraActivity extends AppCompatActivity implements OnImageAvailableListener {

    private static final String TAG = "CameraActivity";

    // Permission
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private boolean debug = false;

    private Handler handler;
    private HandlerThread handlerThread;

    public TextSpeaker ts;
    private final int ACT_CHECK_TTS_DATA = 1000;
    private int languageIndex = 0;
    private String[] language = new String[]{"en", "th"};

    public Classifier classifier;
    public Classifier binaryClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        ts = new TextSpeaker(this, language[languageIndex]);

        // TODO - Check TTS data available
        /*Intent ttsIntent = new Intent();
        ttsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(ttsIntent, ACT_CHECK_TTS_DATA);*/

        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }
    }

    @Override
    public synchronized void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        Log.d(TAG, "onPause");

        ts.stopSpeak();

        if (!isFinishing()) {
            Log.d(TAG, "Requesting finish");
            finish();
        }

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Exception!", e);

        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        Log.d(TAG, "onDestroy");
        ts.close();
        if (classifier != null) {
            classifier.close();
        }
        if (binaryClassifier != null) {
            binaryClassifier.close();
        }
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    // TODO - wait for result from intend for TTS
    /*
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        if (requestCode == ACT_CHECK_TTS_DATA) {
            if (resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // installation process for TTS
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }*/

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    setFragment();
                } else {
                    requestPermission();
                }
            }
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(CameraActivity.this, "Camera AND storage permission are required!", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    protected void setFragment() {
        final Fragment fragment = CameraConnectionFragment.newInstance(
                new CameraConnectionFragment.ConnectionCallback() {
                    @Override
                    public void onPreviewSizeChosen(Size size, int cameraRotation) {
                        CameraActivity.this.onPreviewSizeChosen(size, cameraRotation);
                    }
                },
                this,
                getLayoutId(),
                getDesiredPreviewFrameSize()
        );

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                Log.d(TAG, String.format("Initializing buffer %d at size %d", i, buffer.capacity()));
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public void requestRender() {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }

    public void addCallback(final OverlayView.DrawCallback callback) {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.addCallback(callback);
        }
    }

    public void onSetDebug(final boolean debug) {
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            debug = !debug;
            requestRender();
            onSetDebug(debug);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            switchTTSLanguage();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void switchTTSLanguage() {
        languageIndex++;
        if (languageIndex >= language.length) {
            languageIndex = 0;
        }
        Log.d(TAG, "CurrLagIndex: " + languageIndex);
        ts.close();
        ts = new TextSpeaker(this, language[languageIndex]);
    }

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

}
