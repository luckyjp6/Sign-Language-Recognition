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
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.view.ViewGroup;

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
    private Boolean is_sign_mode;
    private String current_text;

    private float offsetX, offsetY;

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
        setupCameraTouchListener();

        // Init imageReader, start image listener
        initImageReader();
        setupImageReaderListener();

        // Set camera manager
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        startCamera();

    }

    private void initPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
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
                processCapturedImage(reader);
            }
        }, null);
    }

    private void setupCameraTouchListener() {
        pictureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 記錄觸摸點與圖片左上角的偏移量
                        offsetX = event.getX() - pictureView.getX();
                        offsetY = event.getY() - pictureView.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // 跟蹤手指移動，更新圖片位置
                        pictureView.setX(event.getX() - offsetX);
                        pictureView.setY(event.getY() - offsetY);
                        break;
                }
                return true;
            }
        });
    }

    private void processCapturedImage(ImageReader reader) {
        if (cameraCaptureSession_imageReader == null) return;
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        int length = buffer.remaining();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        image.close();

        // Rotate image and show the preview
        PyObject rotateModule = py.getModule("Rotation");
        byte[] rotationResult = rotateModule.callAttr("rotation_func", bytes).toJava(byte[].class);

        Bitmap bmp = BitmapFactory.decodeByteArray(rotationResult, 0, rotationResult.length);
        ImageView displayPicture = findViewById(R.id.picture);
        displayPicture.setImageBitmap(bmp);

        // TODO: Add AI entry point here!!
        // PyObject aiModule = py.getModule("AI_file");
        // String aiResult = aiModule.callAttr("func_name", rotationResult).toJava(String.class);

        // String result = aiModule.callAttr("hello_func", bytes).toJava(String.class);
        // TextView txt = findViewById(R.id.textView);
        // txt.setText(result);

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

        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.fullScroll(View.FOCUS_DOWN);
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
        textView.setTextColor(Color.parseColor(text_color));
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

        // show up camera image
        pictureView.setVisibility(View.VISIBLE);
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

        // turn off camera image
        pictureView.setVisibility(View.GONE);
    }

}