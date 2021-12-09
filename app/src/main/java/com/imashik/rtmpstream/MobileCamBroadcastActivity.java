package com.imashik.rtmpstream;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.imashik.rtmpstream.utils.Camera2ApiManager;
import com.imashik.rtmpstream.utils.RtmpCamera2;
import com.pedro.encoder.input.gl.SpriteGestureController;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.rtplibrary.view.OpenGlView;

import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 * {@link com.pedro.rtplibrary.rtmp.RtmpCamera1}
 */
public class MobileCamBroadcastActivity extends AppCompatActivity implements ConnectCheckerRtmp, SurfaceHolder.Callback, View.OnTouchListener {

    private static final int PERMISSIONS_REQUEST_CODE = 101;
    private RtmpCamera2 rtmpCamera2;
    private ImageView img_start_stop;
    TextView txtTimer;

    private OpenGlView openGlView;

    private SharedPreferences pref;
    private final SpriteGestureController spriteGestureController = new SpriteGestureController();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_broadcast_mobile);

        pref = getSharedPreferences("rtmp_stream", MODE_PRIVATE);
        openGlView = findViewById(R.id.surface_view);
        img_start_stop = findViewById(R.id.img_start_stop);
        txtTimer = findViewById(R.id.txt_timer);
        getScreenSize();

        img_start_stop.setOnClickListener(v -> playVideo());

        if (!checkPermission()) {
            requestPermission();
        } else {
            (new Handler()).postDelayed(this::initStreamCamera, 3000);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        int action = motionEvent.getAction();
        if (motionEvent.getPointerCount() > 1) {
            if (action == MotionEvent.ACTION_MOVE) {
                try {
                    rtmpCamera2.setZoom(motionEvent);
                } catch (Exception e) {
                    Log.e("error =", e.toString());
                }
            }
        } else {
            if (action == MotionEvent.ACTION_UP) {
                Log.e("Stop", "Zoom");
            }
        }
        if (spriteGestureController.spriteTouched(view, motionEvent)) {
            spriteGestureController.moveSprite(view, motionEvent);
            spriteGestureController.scaleSprite(motionEvent);
            return true;
        }
        return true;
    }


    private void initStreamCamera() {
        try {
            rtmpCamera2 = new RtmpCamera2(openGlView, this);
            openGlView.getHolder().addCallback(this);
            openGlView.setOnTouchListener(this);
            rtmpCamera2.disableAudio();
            if (rtmpCamera2 != null)
                rtmpCamera2.startPreview();
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }

        // rtmpCamera2.setVideoBitrateOnFly();
        // addFilter();
    }

   /* private void addFilter() {
        AndroidViewFilterRender androidViewFilterRender = new AndroidViewFilterRender();
        androidViewFilterRender.setView(layout);
        try {
            if (rtmpCamera2 != null)
                rtmpCamera2.getGlInterface().setFilter(androidViewFilterRender);
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }*/


    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{RECORD_AUDIO, CAMERA}, PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean writeStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (grantResults.length > 1) {
                    boolean cameraAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    if (writeStorage && cameraAccepted) {
                        initStreamCamera();
                    }
                }
            }
        }
    }

    public boolean checkPermission() {
        int externalStoragePermission = ContextCompat.checkSelfPermission(this, RECORD_AUDIO);
        int cameraPermission = ContextCompat.checkSelfPermission(this, CAMERA);
        return externalStoragePermission == PackageManager.PERMISSION_GRANTED && cameraPermission == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onConnectionSuccessRtmp() {
        runOnUiThread(() -> showToast("Streaming Started!!"));
    }

    private void showToast(String connection_success) {
        Toast.makeText(this, connection_success, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailedRtmp(final String reason) {
        runOnUiThread(() -> {
            showToast("Connection failed. " + reason);
            stopStreaming();
        });
    }

    @Override
    public void onNewBitrateRtmp(long bitrate) {
        //runOnUiThread(() -> showToast("new bitrate " + bitrate));
    }

    @Override
    public void onDisconnectRtmp() {
        runOnUiThread(() -> showToast("Streaming Stopped"));
    }

    @Override
    public void onAuthErrorRtmp() {
        runOnUiThread(() -> showToast("Auth error"));
    }

    @Override
    public void onAuthSuccessRtmp() {
        runOnUiThread(() -> showToast("Auth success"));
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        try {
            if (rtmpCamera2 != null) {
                rtmpCamera2.startPreview();
                // rtmpCamera2.startPreview(CameraHelper.Facing.BACK, screenWidth, screenHeight, 90);
            }
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        try {
            if (rtmpCamera2 != null) {
                if (rtmpCamera2.isStreaming()) {
                    stopStreaming();
                }
                rtmpCamera2.stopPreview();
            }
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }

    private boolean prepareEncoders() {
        try {
            getScreenSize();

            int rotation = CameraHelper.getCameraOrientation(this);
            // width = 640; // return prepareVideo(640, 480, 30, 1200 * 1024, false, rotation);
            // height = 480;
            int fps = 24;//30
            int videoBitrate = 1200 * 1024;
            int audioBitrate = 64000;
            // prepareAudio(64 * 1024, 32000, true, false, false);
            int sampleRate = 44100; //32000

            return rtmpCamera2.prepareVideo(
                    screenWidth,
                    screenHeight,
                    fps,
                    videoBitrate,
                    false,
                    rotation
            ) && rtmpCamera2.prepareAudio(
                    audioBitrate,
                    sampleRate,
                    true,
                    false,
                    false);
        } catch (Exception e) {

            return rtmpCamera2.prepareAudio() && rtmpCamera2.prepareVideo();
        }
    }


    long updatedTime = 0L;
    private final Handler customHandler = new Handler();
    private long startTime = 0L;

    private final Runnable updateTimerThread = new Runnable() {
        public void run() {
            updatedTime = SystemClock.uptimeMillis() - startTime;
            int secs = (int) (updatedTime / 1000);
            int minutes = secs / 60;
            secs = secs % 60;
            txtTimer.setText(String.format("%02d", minutes) + ":" + String.format("%02d", secs));
            customHandler.postDelayed(this, 0);
        }
    };


    private void playVideo() {
        try {
            if (rtmpCamera2 != null) {
                if (!rtmpCamera2.isStreaming()) {
                    if (prepareEncoders()) {
                        img_start_stop.setImageResource(android.R.drawable.ic_media_pause);
                        startTime = SystemClock.uptimeMillis() - updatedTime;
                        customHandler.postDelayed(updateTimerThread, 0);
                        String rtmpBaseUrl = "rtmp://a.rtmp.youtube.com/live2/";
                        rtmpCamera2.startStream(rtmpBaseUrl + "myStreamKey");
                    } else {
                        showToast("Error preparing stream, This device cant do it");
                    }
                } else {
                    stopStreaming();
                }
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "er " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    void stopStreaming() {
        try {
            if (rtmpCamera2 != null) {
                rtmpCamera2.stopStream();
            }
            img_start_stop.setImageResource(android.R.drawable.ic_media_play);
            customHandler.removeCallbacks(updateTimerThread);
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }

    int screenWidth;
    int screenHeight;

    void getScreenSize() {
        screenWidth = pref.getInt("SP_RESOLUTION_WIDTH", 0);
        screenHeight = pref.getInt("SP_RESOLUTION_HEIGHT", 0);
        if (screenWidth == 0 || screenHeight == 0) {
            setDefaultResolution();
        }
    }

    void setDefaultResolution() {
        try {
            Size[] cameraResolutions = new Camera2ApiManager(this).getCameraResolutionsBack();
            findOptimalResolution(cameraResolutions);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void findOptimalResolution(Size[] cameraResolutions) {
        int selectedPos = -1;

        for (int i = 0; i < cameraResolutions.length; i++) {
            Size size = cameraResolutions[i];
            if (size.getHeight() == 720 && size.getWidth() >= 1000) {
                selectedPos = i;
            }
            if (720 == size.getHeight() && 1280 == size.getWidth()) {
                selectedPos = i;
                break;
            }
        }

        final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

        if (selectedPos >= 0) {
            Size cameraResolution = cameraResolutions[selectedPos];
            width = cameraResolution.getWidth();
            height = cameraResolution.getHeight();
        }
        SharedPreferences.Editor edit = pref.edit();
        edit.putInt("SP_RESOLUTION_WIDTH", width);
        edit.putInt("SP_RESOLUTION_HEIGHT", height);
        edit.apply();
        getScreenSize();
    }

}


