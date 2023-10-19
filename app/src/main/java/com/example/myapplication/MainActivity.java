package com.example.myapplication;

import static android.Manifest.permission.CAMERA;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Picture;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.net.wifi.aware.Characteristics;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Surface;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import com.chaquo.python.PyObject;


import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

//    private TextureView textureView;
    private ImageView pictureView;
    private CameraCaptureSession cameraCaptureSession;
    private CameraCaptureSession cameraCaptureSession_imageReader;
    private String stringCameraID;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest.Builder captureRequestBuilder_imgReader;
    private ImageReader imageReader;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private static ParcelFileDescriptor[] img_pipe, text_pipe;
    private Python py;
    private int frame_count = 0;
    byte [][]frames;

//    static {
//        try {
//            img_pipe = ParcelFileDescriptor.createPipe();
//            text_pipe = ParcelFileDescriptor.createPipe();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        Request camera permission
        ActivityCompat.requestPermissions(this,
                new String[]{CAMERA},
                PackageManager.PERMISSION_GRANTED);

//        access the texture for camera
        pictureView = findViewById(R.id.picture);

//        set camera manager
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

//        Log.d("width",  String.format("value = %d", pictureView.getLayoutParams().width));
//        Log.d("height", String.format("value = %d", pictureView.getLayoutParams().height));
        frames = new byte[20][];

//        start Python
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();

//        prepare imageReader
        imageReader = ImageReader.newInstance(
                pictureView.getLayoutParams().width,
                pictureView.getLayoutParams().height,
                ImageFormat.JPEG,2
        );
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (cameraCaptureSession_imageReader == null) return;
                Image image = reader.acquireLatestImage();
                if (image == null) return;
                ByteBuffer buffer= image.getPlanes()[0].getBuffer();
                int length= buffer.remaining();
                byte[] bytes= new byte[length];

                frames[frame_count] = new byte[length];
                buffer.get(frames[frame_count]);

                image.close();

                Bitmap bmp = BitmapFactory.decodeByteArray(frames[frame_count], 0, length);
                ImageView displayPicture = findViewById(R.id.picture);
                displayPicture.setImageBitmap(bmp);

                frame_count++;
//                Send frames to AI module when 20 frames are collected
                if (frame_count == 1) {
                    frame_count = 0;
//                Rotate image and show the preview
                    PyObject rotateModule = py.getModule("Rotation");
                    rotateModule.callAttr("rotation_func", frames);

//                TODO: Add AI entry point here!!
//                PyObject aiModule = py.getModule("AI_file");
//                String aiResult = aiModule.callAttr("func_name", rotationResult).toJava(String.class);

//                String result = aiModule.callAttr("hello_func", bytes).toJava(String.class);
//                TextView txt = findViewById(R.id.textView);
//                txt.setText(result);
                }


            }
        }, null);

//      prepare texture listener
//        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
//            @Override
//            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
////                startCamera();
//            }
//
//            @Override
//            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
//
//            }
//
//            @Override
//            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
//                return false;
//            }
//
//            @Override
//            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
//
//            }
//        };
//        textureView.setSurfaceTextureListener(surfaceTextureListener);

        startCamera();
    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };


    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    private void startCamera() {
        try {
            stringCameraID = cameraManager.getCameraIdList()[1];
            ImageView displayPicture = findViewById(R.id.picture);

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{CAMERA}, 1);
                // TODO:  public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            cameraManager.openCamera(stringCameraID, stateCallback, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void buttonStartCamera(View view) throws CameraAccessException {
        try {
            captureRequestBuilder_imgReader = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        captureRequestBuilder_imgReader.addTarget(imageReader.getSurface());

        OutputConfiguration outputConfiguration = new OutputConfiguration(imageReader.getSurface());
        SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                Collections.singletonList(outputConfiguration),
                getMainExecutor(),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        if (session == null) return;
                        cameraCaptureSession_imageReader = session;
                        try {
                            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraManager.getCameraIdList()[1]);

//                            Activity activity= getActivity();
                            captureRequestBuilder_imgReader.set(
                                    CaptureRequest.JPEG_ORIENTATION,
                                    getJpegOrientation(characteristics, 180)
                            );
                        } catch (CameraAccessException e) {
                            throw new RuntimeException(e);
                        }
                        captureRequestBuilder_imgReader.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

                        try {
                            cameraCaptureSession_imageReader.setRepeatingRequest(captureRequestBuilder_imgReader.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
//                        unlockFocus();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        cameraCaptureSession_imageReader = null;
                    }
                }
        );
        cameraDevice.createCaptureSession(sessionConfiguration);
////        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
////        Surface surface = new Surface(surfaceTexture);
//        Surface imageSurface = imageReader.getSurface();
//
//        try {
//            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
////            captureRequestBuilder.addTarget(surface);
//            captureRequestBuilder.addTarget(imageSurface);
//
////            OutputConfiguration outputConfiguration = new OutputConfiguration(surface);
//            OutputConfiguration outputConfiguration_image = new OutputConfiguration(imageSurface);
//            Vector<OutputConfiguration> outputVec = new Vector<OutputConfiguration>();
////            outputVec.add(outputConfiguration);
//            outputVec.add(outputConfiguration_image);
//
//            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
//                    Collections.list(outputVec.elements()),
////                    Collections.singletonList(outputConfiguration),
//                    getMainExecutor(),
//                    new CameraCaptureSession.StateCallback() {
//                        @Override
//                        public void onConfigured(@NonNull CameraCaptureSession session) {
//                            cameraCaptureSession = session;
//                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
//                                    CameraMetadata.CONTROL_MODE_AUTO);
//
//                            try {
//                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
//                            } catch (CameraAccessException e) {
//                               e.printStackTrace();
//                            }
//                        }
//                        @Override
//                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//                            cameraCaptureSession = null;
//                        }
//                    }
//            );
//
//            cameraDevice.createCaptureSession(sessionConfiguration);
//        } catch (CameraAccessException e) {
//            throw new RuntimeException(e);
//        }
    }
    public void buttonStopCamera(View view) {
        try {
            if (cameraCaptureSession_imageReader != null) cameraCaptureSession_imageReader.abortCaptures();
        } catch (Exception e) {
//            e.printStackTrace();
        }
        if (cameraCaptureSession != null) cameraCaptureSession.close();
//        if (cameraDevice != null) cameraDevice.close(); // this will shut down the app, don't use it
    }

    public void capture_image(View view) throws CameraAccessException {

//        try {
//            captureRequestBuilder_imgReader = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//        captureRequestBuilder_imgReader.addTarget(imageReader.getSurface());
//
//        OutputConfiguration outputConfiguration = new OutputConfiguration(imageReader.getSurface());
//        SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
//                Collections.singletonList(outputConfiguration),
//                getMainExecutor(),
//                new CameraCaptureSession.StateCallback() {
//                    @Override
//                    public void onConfigured(@NonNull CameraCaptureSession session) {
//                        if (session == null) return;
//                        cameraCaptureSession_imageReader = session;
//                        captureRequestBuilder_imgReader.set(CaptureRequest.JPEG_ORIENTATION, 90);
//                        captureRequestBuilder_imgReader.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//
//                        try {
//                            cameraCaptureSession_imageReader.setRepeatingRequest(captureRequestBuilder_imgReader.build(), null, null);
//                        } catch (CameraAccessException e) {
//                            e.printStackTrace();
//                        }
////                        unlockFocus();
//                    }
//
//                    @Override
//                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//                        cameraCaptureSession_imageReader = null;
//                    }
//                }
//        );
//        cameraDevice.createCaptureSession(sessionConfiguration);
    }


//    private String getStringImage(Bitmap bitmap) {
//        ByteArrayOutputStream bOutStream = new ByteArrayOutputStream();
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bOutStream);
//
////        store in byte array
//        byte[] imageBytes = bOutStream.toByteArray();
////        encode to string
//        String encodedImage = android.util.Base64.encodeToString(imageBytes, Base64.DEFAULT);
//        return encodedImage;
//    }
}