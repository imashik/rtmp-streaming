package com.imashik.rtmpstream;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.imashik.rtmpstream.utils.Camera2ApiManager;
import com.imashik.rtmpstream.usbcamUtils.CameraDialog;
import com.imashik.rtmpstream.usbcamUtils.RtmpUSB;
import com.pedro.rtplibrary.view.OpenGlView;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.common.UVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import java.util.List;


/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 * {@link com.pedro.rtplibrary.rtmp.RtmpCamera1}
 */
public class MultiUsbCamBroadcastActivity extends BaseActivity implements ConnectCheckerRtmp, SurfaceHolder.Callback, CameraDialog.CameraDialogParent {
    private final int width = 1280;
    private final int height = 720;
    SharedPreferences pref;

    private static final int PERMISSIONS_REQUEST_CODE = 101;

    private USBMonitor usbMonitor;
    private UVCCamera uvcCamera;
    private RtmpUSB rtmpUSB;

    private ImageView img_start_stop;
    TextView txtTimer;
    private OpenGlView openGlView;

    private static final float[] BANDWIDTH_FACTORS = {0.5f, 0.5f};

    //USB camera1
    private UVCCameraHandler mHandlerL;
    private CameraViewInterface mUVCCameraViewL;
    private Surface mLeftPreviewSurface;
    private int deviceIdL = 0;

    //USB camera2
    private UVCCameraHandler mHandlerM;
    private CameraViewInterface mUVCCameraViewM;
    private Surface mMiddlePreviewSurface;
    private int deviceIdM = 0;

    //USB camera3
    private UVCCameraHandler mHandlerR;
    private CameraViewInterface mUVCCameraViewR;
    private Surface mRightPreviewSurface;
    private int deviceIdR = 0;

    private final Object mSync = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_broadcast_multicam);

        pref = getSharedPreferences("rtmp_stream", MODE_PRIVATE);
        img_start_stop = findViewById(R.id.img_start_stop);
        txtTimer = findViewById(R.id.txt_timer);

        initCameraViews();
        getScreenSize();
        initClickListeners();

        if (!checkPermissionGranted()) {
            requestPermission();
        } else {
            initStreamCamera();
        }
    }

    private void initClickListeners() {
        img_start_stop.setOnClickListener(v -> playVideo());
    }

    private void initCameraViews() {
        openGlView = findViewById(R.id.surface_view);

        mUVCCameraViewL = findViewById(R.id.surface_view_1);
        mUVCCameraViewL.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        //    mUVCCameraViewL.setCallback(mCallback);
        ((UVCCameraTextureView) mUVCCameraViewL).setOnClickListener(mOnClickListener);
        mHandlerL = UVCCameraHandler.createHandler(this, mUVCCameraViewL, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, BANDWIDTH_FACTORS[0]);


        mUVCCameraViewM = findViewById(R.id.surface_view_2);
        mUVCCameraViewM.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        //    mUVCCameraViewL.setCallback(mCallback);
        ((UVCCameraTextureView) mUVCCameraViewM).setOnClickListener(mOnClickListener);
        mHandlerM = UVCCameraHandler.createHandler(this, mUVCCameraViewM, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, BANDWIDTH_FACTORS[0]);

        mUVCCameraViewR = findViewById(R.id.surface_view_3);
        mUVCCameraViewR.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        //    mUVCCameraViewL.setCallback(mCallback);
        ((UVCCameraTextureView) mUVCCameraViewR).setOnClickListener(mOnClickListener);
        mHandlerR = UVCCameraHandler.createHandler(this, mUVCCameraViewR, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, BANDWIDTH_FACTORS[0]);

    }

    private void initStreamCamera() {
        try {
            rtmpUSB = new RtmpUSB(openGlView, this);
            synchronized (mSync) {
                usbMonitor = new USBMonitor(this, onDeviceConnectListener);
                openGlView.getHolder().addCallback(this);
                rtmpUSB.enableAudio();
            }
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        synchronized (mSync) {
            if (usbMonitor != null)
                usbMonitor.register();
            if (uvcCamera != null)
                uvcCamera.startPreview();

            /*  */

            if (mUVCCameraViewL != null)
                mUVCCameraViewL.onResume();
            if (mUVCCameraViewM != null)
                mUVCCameraViewM.onResume();
            if (mUVCCameraViewR != null)
                mUVCCameraViewR.onResume();
        }

    }

    @Override
    protected void onStop() {
        synchronized (mSync) {
            /**/

            if (mHandlerL != null)
                mHandlerL.close();
            if (mUVCCameraViewL != null)
                mUVCCameraViewL.onPause();

            if (mHandlerM != null)
                mHandlerM.close();
            if (mUVCCameraViewM != null)
                mUVCCameraViewM.onPause();

            if (mHandlerR != null)
                mHandlerR.close();
            if (mUVCCameraViewR != null)
                mUVCCameraViewR.onPause();


            if (uvcCamera != null) {
                uvcCamera.stopPreview();
            }
            if (usbMonitor != null)
                usbMonitor.unregister();
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        try {
            if (rtmpUSB != null) {
                if (rtmpUSB.isStreaming() && uvcCamera != null)
                    rtmpUSB.stopStream(uvcCamera);
                if (rtmpUSB.isOnPreview() && uvcCamera != null)
                    rtmpUSB.stopPreview(uvcCamera);
            }

            synchronized (mSync) {
                if (mHandlerL != null) {
                    mHandlerL = null;
                }
                if (mHandlerM != null) {
                    mHandlerM = null;
                }
                if (mHandlerR != null) {
                    mHandlerR = null;
                }

                if (uvcCamera != null) {
                    uvcCamera.close();
                    if (uvcCamera != null)
                        uvcCamera.destroy();
                    uvcCamera = null;
                }
                if (usbMonitor != null) {
                    usbMonitor.unregister();
                    if (usbMonitor != null)
                        usbMonitor.destroy();
                    usbMonitor = null;
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        super.onDestroy();
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
       /* try {*
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
        /*try {*
            if (rtmpCamera2 != null) {
                if (rtmpCamera2.isStreaming()) {
                    stopStreaming();
                }
                rtmpCamera2.stopPreview();
            }
        } catch (Exception e) {
            Log.e("error =", e.toString());
        }*/
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
                    startStream();
                } else {
                    stopStreaming();
                }
            }

        } catch (Exception e) {
            //Log.e("error =", e.toString());
            Toast.makeText(getApplicationContext(), "er " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    void stopStreaming() {
        try {
            if (rtmpUSB != null) {
                rtmpUSB.stopStream(uvcCamera);
            }
            img_start_stop.setImageResource(android.R.drawable.ic_media_play);
            customHandler.removeCallbacks(updateTimerThread);
            isStreaming = false;
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

    private void startStream() {
        try {
            if (rtmpUSB != null) {
                if (rtmpUSB.prepareVideo(width, height, 30, 4000 * 1024, false, 0, uvcCamera) && rtmpUSB.prepareAudio()) {
                    String rtmpBaseUrl = "rtmp://a.rtmp.youtube.com/live2/";
                    rtmpUSB.startStream(uvcCamera, rtmpBaseUrl + "myStreamKey");
                    isStreaming = true;
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
                    usbMonitor.requestPermission(device);
                    // USBMonitor.UsbControlBlock ctrlBlock = usbMonitor.openDevice(device);
                    // startUsbCamPreview(ctrlBlock);*
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
            startUsbCamPreview(device, ctrlBlock);
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            closeUsbCam(device);
        }

        @Override
        public void onCancel(UsbDevice device) {

        }
    };
    int selectedCamId = 0;
    boolean isStreaming = false;

    private void startUsbCamPreview(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
        try {
            if (selectedCamId == 0 || selectedCamId == device.getDeviceId()) {
                initRTMPCameraView(ctrlBlock);
                selectedCamId = device.getDeviceId();
                if (isStreaming)
                    startStream();
            } else if (!mHandlerL.isOpened() && (deviceIdL == 0 || deviceIdL == device.getDeviceId())) {
                mHandlerL.open(ctrlBlock);
                final SurfaceTexture st = mUVCCameraViewL.getSurfaceTexture();
                mHandlerL.startPreview(new Surface(st));
                deviceIdL = device.getDeviceId();

            } else if (!mHandlerM.isOpened() && (deviceIdM == 0 || deviceIdM == device.getDeviceId())) {
                mHandlerM.open(ctrlBlock);
                final SurfaceTexture st = mUVCCameraViewM.getSurfaceTexture();
                mHandlerM.startPreview(new Surface(st));
                deviceIdM = device.getDeviceId();

            } else if (!mHandlerR.isOpened() && (deviceIdR == 0 || deviceIdR == device.getDeviceId())) {
                mHandlerR.open(ctrlBlock);
                final SurfaceTexture st = mUVCCameraViewR.getSurfaceTexture();
                mHandlerR.startPreview(new Surface(st));
                deviceIdR = device.getDeviceId();
            }
            //UsbDevice device1 = uvcCamera.getDevice();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initRTMPCameraView(USBMonitor.UsbControlBlock ctrlBlock) {
        // resetRTMPStreaming();
        UVCCamera camera = new UVCCamera();
        camera.open(ctrlBlock);
        try {
            camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
        } catch (IllegalArgumentException e) {
            camera.destroy();
            try {
                camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE);
                // camera.set
            } catch (IllegalArgumentException e1) {
                return;
            }
        }
        uvcCamera = camera;
        rtmpUSB.startPreview(uvcCamera, width, height); //UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT
        //  rtmpUSB.startPreview(uvcCamera, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT);
    }


    void closeUsbCam(UsbDevice device) {
        if (uvcCamera != null) {
            uvcCamera.close();
            uvcCamera = null;
        }
        if ((mHandlerL != null) && mHandlerL.isEqual(device)) {
            queueEvent(() -> {
                if (mHandlerL != null)
                    mHandlerL.close();
                if (mLeftPreviewSurface != null) {
                    mLeftPreviewSurface.release();
                    mLeftPreviewSurface = null;
                }
            }, 0);
        } else if ((mHandlerM != null) && mHandlerM.isEqual(device)) {
            queueEvent(() -> {
                if (mHandlerM != null)
                    mHandlerM.close();
                if (mMiddlePreviewSurface != null) {
                    mMiddlePreviewSurface.release();
                    mMiddlePreviewSurface = null;
                }
            }, 0);
        } else if ((mHandlerR != null) && mHandlerR.isEqual(device)) {
            queueEvent(() -> {
                if (mHandlerR != null)
                    mHandlerR.close();
                if (mRightPreviewSurface != null) {
                    mRightPreviewSurface.release();
                    mRightPreviewSurface = null;
                }
            }, 0);
        }
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            switch (view.getId()) {
                case R.id.surface_view_1:
                    if (mHandlerL != null) {
                        if (mHandlerL.isOpened()) {
                            List<UsbDevice> deviceList = usbMonitor.getDeviceList();
                            UsbDevice mainCam = null, secondaryCam = null;
                            for (int i = 0; i < deviceList.size(); i++) {
                                UsbDevice usbDevice = deviceList.get(i);
                                //UVCCameraHandler mHandlerLm = mHandlerL;
                                if (mHandlerL.isEqual(usbDevice)) {
                                    mainCam = usbDevice;
                                } else if (uvcCamera.getDevice().getDeviceId() == usbDevice.getDeviceId()) {
                                    secondaryCam = usbDevice;
                                }
                                if (mainCam != null && secondaryCam != null)
                                    break;
                            }

                            mHandlerL.close();
                            resetRTMPStreaming();
                            if (mainCam != null) {
                                selectedCamId = mainCam.getDeviceId();
                                usbMonitor.requestPermission(mainCam);
                            }

                            if (secondaryCam != null) {
                                deviceIdL = secondaryCam.getDeviceId();
                                usbMonitor.requestPermission(secondaryCam);
                            }
                        } else {
                            CameraDialog.showDialog(MultiUsbCamBroadcastActivity.this);
                        }
                    }
                    break;

                case R.id.surface_view_2:
                    if (mHandlerM != null) {
                        if (mHandlerM.isOpened()) {
                            List<UsbDevice> deviceList = usbMonitor.getDeviceList();
                            UsbDevice mainCam = null, secondaryCam = null;
                            for (int i = 0; i < deviceList.size(); i++) {
                                UsbDevice usbDevice = deviceList.get(i);
                                //UVCCameraHandler mHandlerLm = mHandlerM;
                                if (mHandlerM.isEqual(usbDevice)) {
                                    mainCam = usbDevice;
                                } else if (selectedCamId == usbDevice.getDeviceId()) {//selectedCamId
                                    secondaryCam = usbDevice;
                                }
                                if (mainCam != null && secondaryCam != null)
                                    break;
                            }

                            mHandlerM.close();
                            resetRTMPStreaming();

                            if (mainCam != null) {
                                selectedCamId = mainCam.getDeviceId();
                                usbMonitor.requestPermission(mainCam);
                            }

                            if (secondaryCam != null) {
                                deviceIdM = secondaryCam.getDeviceId();
                                usbMonitor.requestPermission(secondaryCam);
                            }

                        } else {
                            CameraDialog.showDialog(MultiUsbCamBroadcastActivity.this);
                        }
                    }
                    break;

                case R.id.surface_view_3:
                    if (mHandlerR != null) {
                        if (mHandlerR.isOpened()) {
                            List<UsbDevice> deviceList = usbMonitor.getDeviceList();
                            UsbDevice mainCam = null, secondaryCam = null;
                            for (int i = 0; i < deviceList.size(); i++) {
                                UsbDevice usbDevice = deviceList.get(i);
                                //UVCCameraHandler mHandlerLm = mHandlerR;
                                if (mHandlerR.isEqual(usbDevice)) {
                                    mainCam = usbDevice;
                                } else if (selectedCamId == usbDevice.getDeviceId()) {
                                    secondaryCam = usbDevice;
                                }
                                if (mainCam != null && secondaryCam != null)
                                    break;
                            }

                            mHandlerR.close();
                            resetRTMPStreaming();

                            if (mainCam != null) {
                                selectedCamId = mainCam.getDeviceId();
                                usbMonitor.requestPermission(mainCam);
                            }
                            if (secondaryCam != null) {
                                deviceIdR = secondaryCam.getDeviceId();
                                usbMonitor.requestPermission(secondaryCam);
                            }
                        } else {
                            CameraDialog.showDialog(MultiUsbCamBroadcastActivity.this);
                        }
                    }
                    break;

            }
        }
    };

    private void resetRTMPStreaming() {
        if (rtmpUSB.isStreaming() && uvcCamera != null)
            rtmpUSB.stopStream(uvcCamera);
        if (rtmpUSB.isOnPreview() && uvcCamera != null)
            rtmpUSB.stopPreview(uvcCamera);
        if (uvcCamera != null) {
            uvcCamera.close();
        }
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return usbMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            runOnUiThread(() -> {
                //  setCameraButton();
            }, 0);
        }
    }
}

