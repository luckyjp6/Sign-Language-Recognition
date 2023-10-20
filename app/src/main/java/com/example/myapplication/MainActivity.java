package com.example.myapplication;

import static android.Manifest.permission.CAMERA;

import static java.lang.System.exit;

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
import android.view.View;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import com.chaquo.python.PyObject;

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
    private ImageView pictureView;
    private CameraCaptureSession cameraCaptureSession_imageReader;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder_imgReader;
    private ImageReader imageReader;
    private Python py;
    private String threadInstruction;
    private byte [] frame;
    private Bitmap bmp;
    private String model_return;
    private Boolean is_sign_mode;
    private String current_text;
    private int skipCount = 0;

    private float offsetX, offsetY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start socket thread
        mThread initThread = new mThread();
        threadInstruction = "init";
        initThread.start();

        // Start Python
//        initPython();

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

        try {
            initThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

//    private void initPython() {
//        if (!Python.isStarted()) {
//            Python.start(new AndroidPlatform(this));
//        }
//    }

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
                frame = new byte[length];
                buffer.get(frame);

                // Show preview
                bmp = BitmapFactory.decodeByteArray(frame, 0, length);
                ImageView displayPicture = findViewById(R.id.picture);
                displayPicture.setImageBitmap(bmp);

                skipCount++;
//
                if (skipCount == 10) {
                    // Send image to AI module
                    skipCount = 0;
                    mThread socketThread = new mThread();
                    socketThread.start();
                }

                image.close();

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

    public class mThread extends Thread {
        @Override
        public void run() {
            super.run();

            // Start socket
            SocketClient socketClient = new SocketClient();

            // Send request of frame
            if (threadInstruction.isEmpty()) socketClient.send_frame();
            else if (threadInstruction.equals("request")) {
                socketClient.send_request();
                threadInstruction = "";
            }
            else if (threadInstruction.equals("init")) {
                socketClient.send_init();
                threadInstruction = "";
            }
        }
    }

    public class SocketClient {
        private Socket aiSever;
//        private PrintWriter printWriter;
        private BufferedReader bufferedReader;

        private void init () {
            try {
                aiSever = new Socket("140.113.141.90", 12345);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private void close() {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (aiSever != null) {
                try {
                    aiSever.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        public void send_init () {
            init();

            OutputStream outputStream;
            try {
                outputStream = aiSever.getOutputStream();
                outputStream.write("init".getBytes());
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            close();
        }
        public void send_request() {
            init();
            OutputStream outputStream;
            try {
                outputStream = aiSever.getOutputStream();
                outputStream.write("request".getBytes());
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                bufferedReader = new BufferedReader(new InputStreamReader(aiSever.getInputStream()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                model_return = bufferedReader.readLine(); // read is also available, but it returns char[]
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            close();
        }
        public void send_frame () {
            init();
//            Log.d("length", Integer.toString(frame.length));
            OutputStream outputStream;
            try {

                outputStream = aiSever.getOutputStream();
                outputStream.write(Integer.toString(frame.length).getBytes());
                outputStream.write(frame);
//                bmp.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            close();
        }
    }

    private void processCapturedImage() {
        // Get text result from AI module
        mThread requestThread = new mThread();
        threadInstruction = "request";
        requestThread.start();

        // Wait for the result
        try {
            requestThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        PyObject sendFrameModule = py.getModule("Connect");
//        String result = sendFrameModule.callAttr("get_text").toJava(String.class);

//        String model_return; -> I move this as global variable, so as to modify its value in another thread

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
        LinearLayout command_icon = findViewById(R.id.command_icon);
        if (new_text.length() > 0 && new_text.charAt(0) == '@') {
            // Check if the string is not empty and the first character is "@"
            new_text = new_text.substring(1); // Remove the first character
            current_text = (current_text != null) ? current_text + " " + new_text : new_text;

            // Switch mode to command mode
            is_sign_mode = Boolean.FALSE;
            // To hide all icons
            command_icon.setVisibility(View.VISIBLE);
        }
        else if(new_text.equals("#")){ // enter
            if (current_text != null) {
                LinearLayout linearLayout = findViewById(R.id.convo);
                addStyledTextViewToLayout(linearLayout, current_text, true);
                current_text = null;
            }
        }
        else if(new_text.equals("$")){ // restart
            // switch mode to Sign mode
            current_text = null;
            is_sign_mode = Boolean.TRUE;
            // To hide all icons
            command_icon.setVisibility(View.GONE);
        }
        else if(new_text.equals("%")){ // delete
            current_text = null;
        }
        else if(new_text.equals("^")){ // exit
            current_text = null;
            // TODO: some function to close the camera texture

            // To hide all icons
            command_icon.setVisibility(View.GONE);
        }
        else if(new_text.equals("&")){ // empty value

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


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
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
            String stringCameraID = cameraManager.getCameraIdList()[1];

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{CAMERA}, 1);
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
                        cameraCaptureSession_imageReader = session;
//                        captureRequestBuilder_imgReader.set(CaptureRequest.JPEG_ORIENTATION, 0);
//                        captureRequestBuilder_imgReader.set(CaptureRequest.JPEG_QUALITY, (byte) 40);
                        captureRequestBuilder_imgReader.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

                        try {
                            cameraCaptureSession_imageReader.setRepeatingRequest(captureRequestBuilder_imgReader.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
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

    public void buttonStopCamera(View view) {
        try {
            if (cameraCaptureSession_imageReader != null) cameraCaptureSession_imageReader.abortCaptures();
        } catch (Exception e) {
            e.printStackTrace();
        }
//        if (cameraDevice != null) cameraDevice.close(); // this will shut down the app, don't use it

        // turn off camera image
        pictureView.setVisibility(View.GONE);
    }

}