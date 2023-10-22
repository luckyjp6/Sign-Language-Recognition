package com.example.myapplication;

import static android.Manifest.permission.CAMERA;

import static java.lang.System.exit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModel;

import android.content.ClipData;
import android.content.ClipDescription;
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
import android.os.Handler;
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
import java.util.concurrent.TimeUnit;

import com.chaquo.python.PyObject;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.MotionEvent;

import android.view.DragEvent;

public class MainActivity extends AppCompatActivity {
    private ImageView pictureView;
    private FrameLayout pictureFrame;
    private ConstraintLayout screenLayout;
    private CameraCaptureSession cameraCaptureSession_imageReader;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder_imgReader;
    private ImageReader imageReader;
    private Python py;
    private String threadInstruction = "";
    private byte [] frame;
    private int MAX_FRAME_NUM = 10;
    private int collectedFrames = 0;
    private byte [][] frames;
    private Bitmap bmp;
    private String model_return;
    private Boolean is_sign_mode = Boolean.FALSE;
    private String current_text;
    private int skipCount = 0;
    private float drag_x, drag_y;
    private Boolean cameraInScreen;
    private Boolean is_robot;
    private robotResponse robot;

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

        frames = new byte[MAX_FRAME_NUM][50000];

        // Request camera permission
        ActivityCompat.requestPermissions(this,
                new String[]{CAMERA},
                PackageManager.PERMISSION_GRANTED);

        // Initialize texture for camera
        pictureView = findViewById(R.id.picture);

        // Initialize picture FrameLayout, start Camera touch listener
        pictureFrame = findViewById(R.id.picture_frame);
        pictureFrame.setVisibility(View.GONE);
        setupCameraTouchListener();

        // Initialize  Screen, start Drag Listener
        screenLayout = findViewById(R.id.screen_layout);
        setupLayoutOnDragListener();

        // Init imageReader, start image listener
        initImageReader();
        setupImageReaderListener();

        // robot response
        robot = new robotResponse();
        robot.welcome_mas();


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
//                frames[collectedFrames] = new byte[length];
//                buffer.get(frames[collectedFrames]);

                // Show preview
                bmp = BitmapFactory.decodeByteArray(frame, 0, length);
//                bmp = BitmapFactory.decodeByteArray(frames[collectedFrames], 0, length);
                ImageView displayPicture = findViewById(R.id.picture);
                displayPicture.setImageBitmap(bmp);

                skipCount++;
//
                if (skipCount == 5) {
                    skipCount = 0;
                    mThread frameThread = new mThread();
                    frameThread.start();
//                    skipCount = 0;
//                    frames[collectedFrames] = frame;
//                    if (frames[collectedFrames] == null) return;

                    collectedFrames++;
//
                    if (collectedFrames == MAX_FRAME_NUM) {
                        collectedFrames = 0;
                        try {
                            frameThread.join();
                        } catch ( InterruptedException e) {
                            throw new RuntimeException();
                        }

                        mThread requestThread = new mThread();
                        threadInstruction = "request";
                        requestThread.start();

                        try {
                            requestThread.join();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
//                    }
//                    exit(0);
                        processCapturedImage();
                    }
                }

                image.close();
            }
        }, null);
    }

    private void setupCameraTouchListener() {
        pictureFrame.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: // when click/press the camera -> start to drag
                        CharSequence charSequence = (CharSequence) pictureFrame.getTag();
                        ClipData.Item item = new ClipData.Item(charSequence);
                        ClipData clipData = new ClipData(charSequence, new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
                        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(pictureFrame);
                        pictureFrame.startDragAndDrop(clipData, shadowBuilder, null, 0);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });
    }

    private void setupLayoutOnDragListener(){
        screenLayout.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED: // start to drag camera
                        pictureFrame.setVisibility(View.GONE); // let original camera disappear
                        return true;
                    case DragEvent.ACTION_DRAG_ENTERED: // camera in screen
                        cameraInScreen = true;
                        return true;
                    case DragEvent.ACTION_DRAG_LOCATION: // dragging in the screen
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED: // camera out of screen
                        cameraInScreen = false;
                        return true;
                    case DragEvent.ACTION_DROP: // record the last x&y of camera (left corner)
                        drag_x = event.getX();
                        drag_y = event.getY();
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED: // drag finish
                        // make sure that the camera is not out of screen & drag success
                        if (cameraInScreen && event.getResult()) { // put it down with camera's center
                            int left = screenLayout.getLeft();
                            int top = screenLayout.getTop();
                            drag_x = drag_x + left - (pictureFrame.getWidth() / 2);
                            drag_y = drag_y + top - (pictureFrame.getHeight() / 2);
                            pictureFrame.setX(drag_x);
                            pictureFrame.setY(drag_y);
                        }
                        pictureFrame.setVisibility(View.VISIBLE);
                        return true;
                }
                return false;
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
        OutputStream outputStream;
//        private PrintWriter printWriter;
        private BufferedReader bufferedReader;

        private void init () {
            try {
                aiSever = new Socket("140.113.141.90", 12345);
                outputStream = aiSever.getOutputStream();
//                bufferedReader = new BufferedReader(new InputStreamReader(aiSever.getInputStream()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
//        private void send_msg(byte[] msg, int length) {
//            init();
//            try {
//                if (length >= 0) outputStream.write(Integer.toString(length).getBytes());
//                outputStream.write(msg);
//                outputStream.flush();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            close();
//        }
//        private byte[] recv_msg() {
//
//        }
        private void close() {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
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

//            OutputStream outputStream;
            try {
                // outputStream = aiSever.getOutputStream();
                outputStream.write("init".getBytes());
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            close();
        }
        public void send_request() {
            // Send request to server
            init();
            try {
                // outputStream = aiSever.getOutputStream();
                //if (is_sign_mode) outputStream.write("request_sign_mode".getBytes());
                //else
                outputStream.write("request".getBytes());
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Get server response
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(aiSever.getInputStream()));
                model_return = bufferedReader.readLine(); // read is also available, but it returns char[]
                if (model_return == null) model_return = "";
                Log.d("############model return", model_return);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            close();
        }
        public void send_frame () {

            init();
            try {
//                outputStream = aiSever.getOutputStream();
                int length = frame.length;
                outputStream.write(Integer.toString(length).getBytes());
                outputStream.flush();
                outputStream.write(frame);
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

//            Get ACK -> use return string
//            try {
//                bufferedReader = new BufferedReader(new InputStreamReader(aiSever.getInputStream()));
//                String ret = bufferedReader.readLine(); // read is also available, but it returns char[]
//                if (ret == "0") {
//                    is_sign_mode = Boolean.FALSE;
//                } else {
//                    is_sign_mode = Boolean.TRUE;
//                }
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }

            close();
        }
    }

    private void processCapturedImage() {
//        Log.d("model return", model_return);
        if (model_return == null) return;
        return_text_processing(model_return);
    }

    public class robotResponse extends ViewModel {
        private int resIndex = 0;
        private ArrayList<String> responseSet;
        private Boolean first_enter;
        public  robotResponse(){
            first_enter = Boolean.TRUE;
            responseSet = new ArrayList<>(Arrays.asList("你好~", "對呀","我是聽人"));
        }
        public void getResponse(String text){
            String response;
            if( text.contains("?")){
                response = responseSet.get(1);
            }
            else if( text.contains("聽人") ){
                response = responseSet.get(2);
            }
            else {
                response = "你在說什麼？";
            }

            LinearLayout linearLayout = findViewById(R.id.convo);
            addStyledTextViewToLayout(linearLayout, response, false);

            // Chatroom update
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.fullScroll(View.FOCUS_DOWN);
        }
        public void welcome_mas(){
            if (first_enter){
                first_enter = Boolean.FALSE;
                LinearLayout linearLayout = findViewById(R.id.convo);
                addStyledTextViewToLayout(linearLayout, responseSet.get(0), false);

                // Chatroom update
                ScrollView scrollView = findViewById(R.id.scrollView);
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        }
    }


    private void button_lit_up(ImageView icon) {
        icon.setBackgroundColor(R.drawable.button_bg);

        // Use a Handler to dismiss the dialog after a short delay (e.g., 2 seconds)
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                icon.setBackgroundResource(0);
            }
        }, 1000); // 1000 milliseconds (2 seconds)
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
//                        captureRequestBuilder_imgReader.set(CaptureRequest.JPEG_QUALITY, (byte) 80);
                        captureRequestBuilder_imgReader.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long)1);
                        captureRequestBuilder_imgReader.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, CameraMetadata.CONTROL_AF_TRIGGER_START);

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
        pictureFrame.setVisibility(View.VISIBLE);

        // disable button of startCamera
        Button startCameraButton = findViewById(R.id.startCamera);
        startCameraButton.setVisibility(View.GONE);

        // remove initial text
        TextView displayText = findViewById(R.id.display_text);
        displayText.setText("");
    }

    public void buttonStopCamera(View view) {
        try {
            if (cameraCaptureSession_imageReader != null) cameraCaptureSession_imageReader.abortCaptures();
        } catch (Exception e) {
            e.printStackTrace();
        }
//        if (cameraDevice != null) cameraDevice.close(); // this will shut down the app, don't use it

        // turn off camera image
        pictureFrame.setVisibility(View.GONE);

        // disable button of startCamera
        Button startCameraButton = findViewById(R.id.startCamera);
        startCameraButton.setVisibility(View.VISIBLE);

        // show initial text
        TextView displayText = findViewById(R.id.display_text);
        displayText.setText("Enter a message");
    }

    private void return_text_processing(String new_text){
        LinearLayout command_icon = findViewById(R.id.command_icon);

        if (new_text.length() > 0 && new_text.charAt(0) == '@') {
            // Check if the string is not empty and the first character is "@"
            new_text = new_text.substring(1); // Remove the first character

            // append new sentence
            current_text = new_text;

            // Switch mode to command mode
            is_sign_mode = Boolean.FALSE;

            // To hide all icons
            command_icon.setVisibility(View.VISIBLE);

            // set current message
            TextView display_text = findViewById(R.id.display_text);
            display_text.setText(current_text);
        }
        else if(new_text.equals("#")){ // enter
            cmd_enter(null);
        }
        else if(new_text.equals("$")){ // restart
            cmd_restart(null);
        }
        else if(new_text.equals("%")){ // delete
            cmd_delete(null);
        }
        else if(new_text.equals("^")){ // exit
            cmd_exit(null);
        }
        else { // regular text
            current_text = (current_text != null) ? current_text + new_text : new_text;

            // set current message
            TextView display_text = findViewById(R.id.display_text);
            display_text.setText(current_text);
        }

        // Chatroom update
        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    public void cmd_restart(View view) {
        LinearLayout command_icon = findViewById(R.id.command_icon);
        ImageView restart_icon = findViewById(R.id.restart);
        // switch mode to Sign mode
        current_text = null;
        is_sign_mode = Boolean.TRUE;

        // lit up restart icon
        button_lit_up(restart_icon);

        // To hide all icons
        // command_icon.setVisibility(View.GONE);

        // set current message
        TextView display_text = findViewById(R.id.display_text);
        display_text.setText(current_text);

        try {
            buttonStartCamera(null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    public void cmd_exit(View view) {
        LinearLayout command_icon = findViewById(R.id.command_icon);
        ImageView exit_icon = findViewById(R.id.exit);

        // lit up exit icon
        button_lit_up(exit_icon);

        buttonStopCamera(null);

        // set current message
        TextView display_text = findViewById(R.id.display_text);
        display_text.setText(current_text);

        // To hide all icons
        // command_icon.setVisibility(View.GONE);
    }

    public void cmd_delete(View view) {
        ImageView delete_icon = findViewById(R.id.delete);
        current_text = null;
        // lit up delete icon
        button_lit_up(delete_icon);

        // show initial text
        TextView displayText = findViewById(R.id.display_text);
        displayText.setText("Enter a message");
    }

    public void cmd_enter(View view) {
        ImageView enter_icon = findViewById(R.id.send);
        if (current_text != null) {
            LinearLayout linearLayout = findViewById(R.id.convo);
            addStyledTextViewToLayout(linearLayout, current_text, true);

            robot.getResponse(current_text);
            current_text = null;
        }
        // lit up enter icon
        button_lit_up(enter_icon);

        // set current message
        TextView display_text = findViewById(R.id.display_text);
        display_text.setText("Enter a message");
    }

    public void send_test_message(View view) {
        // Set the string to the fifth value when Button 5 is clicked
        model_return = "good night! ";
        return_text_processing(model_return);
    }

}