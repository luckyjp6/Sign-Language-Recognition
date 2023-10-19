package com.example.myapplication;

import static android.Manifest.permission.CAMERA;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModel;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
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
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import com.chaquo.python.PyObject;


import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private Boolean is_sign_mode;
    private String current_text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start Python
        initPython();

        // Request camera permission
        ActivityCompat.requestPermissions(this,
                new String[]{CAMERA},
                PackageManager.PERMISSION_GRANTED);

        // Initialize texture for camera
        pictureView = findViewById(R.id.picture);

        // Init imageReader, start image listener
        initImageReader();
        setupImageReaderListener();

        frames = new byte[20][];

        // Set camera manager
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        startCamera();
    }

    private void initPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
        PyObject sendFrameModule = py.getModule("Connect");
        sendFrameModule.callAttr("init_slr");
    }

    private void initImageReader() {
        imageReader = ImageReader.newInstance(
                pictureView.getLayoutParams().width,
                pictureView.getLayoutParams().height,
                ImageFormat.JPEG, 2
        );
    }

    private void setupImageReaderListener() {
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (cameraCaptureSession_imageReader == null) return;
                Image image = reader.acquireLatestImage();
                if (image == null) return;
                ByteBuffer buffer= image.getPlanes()[0].getBuffer();
                int length= buffer.remaining();

                // Get image
                byte[] bytes = new byte[length];
                frames[frame_count] = new byte[length];
                buffer.get(frames[frame_count]);

                image.close();

                // Show preview
                Bitmap bmp = BitmapFactory.decodeByteArray(frames[frame_count], 0, length);
                ImageView displayPicture = findViewById(R.id.picture);
                displayPicture.setImageBitmap(bmp);

                frame_count++;
                
                // Send frames to AI module when 20 frames are collected
                if (frame_count == 20) {
                    frame_count = 0;
                    processCapturedImage(reader, bytes);

                //  Rotate image and show the preview
//                     PyObject rotateModule = py.getModule("Rotation");
//                     rotateModule.callAttr("rotation_func", frames);
                }
            }
        }, null);
    }

    private void processCapturedImage(ImageReader reader, byte[] bytes) {
//      TODO: Add AI entry point here!!
        PyObject sendFrameModule = py.getModule("Connect");
        boolean sendResult = sendFrameModule.callAttr("send_frame", frames).toJava(boolean.class);

        String result = sendFrameModule.callAttr("get_text").toJava(String.class);
        String model_return;

        if (is_sign_mode == Boolean.TRUE) {
            // TODO: 接Sign mode model
            Random random = new Random();

// Generate a random number between 0 and 2 (inclusive)
            int randomValue = random.nextInt(3);

            switch (randomValue) {
                case 0:
                    model_return = "Hello";
                    break;
                case 1:
                    model_return = "#";
                    break;
                case 2:
                    model_return = "@my name is Emerald";
                    break;
                default:
                    model_return = "@Hi"; // Default value in case of unexpected randomValue
            }
        } else {
            // TODO: 接command mode model
            Random random = new Random();

// Generate a random number between 0 and 2 (inclusive)
            int randomValue = random.nextInt(3);

            switch (randomValue) {
                case 0:
                    model_return = "Hello";
                    break;
                case 1:
                    model_return = "#";
                    break;
                case 2:
                    model_return = "@my name is Emerald";
                    break;
                default:
                    model_return = "@Hi";
            }
        }

        return_text_processing(model_return);
    }

    public class textData extends ViewModel {
        public  textData(){
            responseSet = new ArrayList<>(Arrays.asList("要不要一起去吃飯?","要不要找更多人?"));
        }
        private int resIndex = 0;
        private ArrayList<String> responseSet;
        public String getResponse(){
            if(resIndex<responseSet.size()){
                return responseSet.get(resIndex++);
            }
            else return "";
        }

    }

    private void return_text_processing(String new_text){
        if (new_text.length() > 0 && new_text.charAt(0) == '@') {
            // Check if the string is not empty and the first character is "@"
            new_text = new_text.substring(1); // Remove the first character
            current_text = (current_text != null) ? current_text + " " + new_text : new_text;

            // Switch mode to function mode
            is_sign_mode = Boolean.FALSE;
        }
        else if(new_text == "#"){ // enter
            if (current_text != null) {
                LinearLayout linearLayout = findViewById(R.id.convo);
                addStyledTextViewToLayout(linearLayout, current_text, true);
                current_text = null;
            }
        }
        else if(new_text == "$"){ // restart
            // switch mode to Sign mode
            current_text = null;
            is_sign_mode = Boolean.TRUE;
        }
        else if(new_text == "%"){ // delete
            current_text = null;
        }
        else if(new_text == "^"){ // exit
            current_text = null;
            // TODO: some function to close the camera texture
        }
        else if(new_text == "&"){ // empty value

        }
        else{ // regular text
            current_text = (current_text != null) ? current_text + new_text : new_text;
        }
        TextView display_text = findViewById(R.id.display_text);
        display_text.setText(current_text);

    }

    private void addStyledTextViewToLayout(LinearLayout layout, String text, boolean is_my_text) {
        // Create and configure a new TextView
        TextView styledTextView = createStyledTextView(this, text, is_my_text);

        View spacer = new View(this);
        spacer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(10) // Convert dp to pixels (10dp)
        ));

        layout.addView(styledTextView);
        layout.addView(spacer);
    }


    // Function to convert dp to pixels
    public int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private TextView createStyledTextView(Context context, String text, Boolean is_my_text) {
        int gravity = Gravity.START, backgroundResourceID = R.drawable.your_input_bg;
        String text_color = "#4285F4";
        if(is_my_text == Boolean.TRUE){
            gravity = Gravity.END;
            backgroundResourceID = R.drawable.my_input_bg;
            text_color = "#FAF9F6";
        }
        TextView textView = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = gravity;

        textView.setLayoutParams(params);
        textView.setBackgroundResource(backgroundResourceID);
        textView.setPadding(16, 8, 16, 8);
        textView.setText(text);
        textView.setTextColor(Color.parseColor("#FFFFFF"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

        return textView;
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
    }

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

    public void buttonStopCamera(View view) {
        try {
            if (cameraCaptureSession_imageReader != null) cameraCaptureSession_imageReader.abortCaptures();
        } catch (Exception e) {
//            e.printStackTrace();
        }
        if (cameraCaptureSession != null) cameraCaptureSession.close();
//        if (cameraDevice != null) cameraDevice.close(); // this will shut down the app, don't use it
    }

}