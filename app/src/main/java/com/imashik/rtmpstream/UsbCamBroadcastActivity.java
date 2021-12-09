package com.imashik.rtmpstream;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.imashik.rtmpstream.utils.Camera2ApiManager;
import com.imashik.rtmpstream.usbcamUtils.RtmpUSB;
import com.pedro.rtplibrary.view.OpenGlView;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import net.ossrs.rtmp.ConnectCheckerRtmp;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 * {@link com.pedro.rtplibrary.rtmp.RtmpCamera1}
 */
public class UsbCamBroadcastActivity extends AppCompatActivity implements ConnectCheckerRtmp, SurfaceHolder.Callback {//, View.OnTouchListener
    private USBMonitor usbMonitor;
    private UVCCamera uvcCamera;
    private final int width = 1280;
    private final int height = 720;
    private RtmpUSB rtmpUSB;

    @Override
    protected void onDestroy() {
        try {
            if (rtmpUSB != null) {
                if (rtmpUSB.isStreaming() && uvcCamera != null)
                    rtmpUSB.stopStream(uvcCamera);
                if (rtmpUSB.isOnPreview() && uvcCamera != null)
                    rtmpUSB.stopPreview(uvcCamera);
            }

            if (uvcCamera != null)
                uvcCamera.close();

            if (usbMonitor != null)
                usbMonitor.unregister();

            if (uvcCamera != null) {
                uvcCamera.destroy();
            }
            if (usbMonitor != null) {
                usbMonitor.destroy();
            }
            uvcCamera = null;
            usbMonitor = null;
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private void startStream(String url) {
        try {
            if (rtmpUSB != null) {
                if (rtmpUSB.prepareVideo(width, height, 30, 4000 * 1024, false, 0, uvcCamera) && rtmpUSB.prepareAudio()) {
                    rtmpUSB.startStream(uvcCamera, url);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    USBMonitor.OnDeviceConnectListener onDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            if (usbMonitor != null && device != null) {
                if (!usbMonitor.hasPermission(device))
                    usbMonitor.requestPermission(device);
                else {
                    try {
                        USBMonitor.UsbControlBlock ctrlBlock = usbMonitor.openDevice(device);
                        UVCCamera camera = new UVCCamera();
                        camera.open(ctrlBlock);
                        try {
                            camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
                        } catch (IllegalArgumentException e) {
                            camera.destroy();
                            try {
                                camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE);
                            } catch (IllegalArgumentException e1) {
                                return;
                            }
                        }
                        uvcCamera = camera;
                        rtmpUSB.startPreview(uvcCamera, width, height);
                        //UsbDevice device1 = uvcCamera.getDevice();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        }

        @Override
        public void onDettach(UsbDevice device) {
            if (uvcCamera != null) {
                uvcCamera.close();
                uvcCamera = null;
            }
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            try {
                UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock);
                try {
                    camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
                } catch (IllegalArgumentException e) {
                    camera.destroy();
                    try {
                        camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE);
                    } catch (IllegalArgumentException e1) {
                        return;
                    }
                }
                uvcCamera = camera;
                rtmpUSB.startPreview(uvcCamera, width, height);
                //UsbDevice device1 = uvcCamera.getDevice();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            if (uvcCamera != null) {
                uvcCamera.close();
                uvcCamera = null;
            }
        }

        @Override
        public void onCancel(UsbDevice device) {

        }
    };

    private static final int PERMISSIONS_REQUEST_CODE = 101;
    private ImageView img_start_stop;
    TextView txtTimer;

    private OpenGlView openGlView;

    SharedPreferences pref;

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

        if (!checkPermissionGranted()) {
            requestPermission();
        } else {
            initStreamCamera();
        }
    }


    private void initStreamCamera() {
        try {
            rtmpUSB = new RtmpUSB(openGlView, this);
            usbMonitor = new USBMonitor(this, onDeviceConnectListener);
            openGlView.getHolder().addCallback(this);
            //openGlView.setOnTouchListener(this);
            rtmpUSB.enableAudio();
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (usbMonitor != null)
            usbMonitor.register();
    }

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

    public boolean checkPermissionGranted() {
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
       /* try {
            if (rtmpCamera2 != null) {
                rtmpCamera2.startPreview();
                // rtmpCamera2.startPreview(CameraHelper.Facing.BACK, screenWidth, screenHeight, 90);
            }
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }*/
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
//        try {
//            if (rtmpCamera2 != null) {
//                if (rtmpCamera2.isStreaming()) {
//                    stopStreaming();
//                }
//                rtmpCamera2.stopPreview();
//            }
//        } catch (Exception e) {
//            Log.e("error =", e.toString());
//        }
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
            if (uvcCamera != null) {
                if (!rtmpUSB.isStreaming()) {
                    img_start_stop.setImageResource(android.R.drawable.ic_media_pause);
                    startTime = SystemClock.uptimeMillis() - updatedTime;
                    customHandler.postDelayed(updateTimerThread, 0);
                    String rtmpBaseUrl = "rtmp://a.rtmp.youtube.com/live2/";
                    startStream(rtmpBaseUrl + "myStreamKey");
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
            rtmpUSB.stopStream(uvcCamera);
            img_start_stop.setImageResource(android.R.drawable.ic_media_play);
            customHandler.removeCallbacks(updateTimerThread);
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }


    int screenWidth;
    int screenHeight;

    void getScreenSize() {
        screenWidth = pref.getInt("SP_RESOLUTION_HEIGHT", 0);
        screenHeight = pref.getInt("SP_RESOLUTION_WIDTH", 0);

        if (screenWidth == 0 || screenHeight == 0) {
            setDefaultResolution();
        }
    }

    void setDefaultResolution() {
        try {
            /*Size[] cameraResolutions = getCameraResolutionsExternal("0");*/
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