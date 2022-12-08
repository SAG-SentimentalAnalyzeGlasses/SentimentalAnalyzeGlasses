package com.example.sentimentanalyzer;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 201;
    private static String LOGTAG = "OpenCV_Log";
    private facialExpressionRecognition facialExpressionRecognition;
    private Mat mRgba;
    private Mat mGray;
    private boolean recording = false;
    private ArrayList<Float> faceRecognitionResult = new ArrayList<Float>();

    private Intent intent;
    private CameraBridgeViewBase mOpenCvCameraView;
    private EditText editText;
    private ImageView imageView;
    private Button button;
    private SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(CameraActivity.this); // 새 SpeechRecognizer 를 만드는 팩토리 메서드
    private BackgroundThread mThread;

    /*usb camera*/
//    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

    /*기기 열거*/
//    UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
//
//    HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
//    UsbDevice device = deviceList.get("deviceName");
//
//
//    Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
//    while(deviceIterator.hasNext()){
//        UsbDevice device = deviceIterator.next();
//        //your code
//    }


    /*엑세스 권한 런타임*/
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                        }
                    }
                    else {
                        Log.v(LOGTAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.v(LOGTAG, "OpenCV Loaded");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getSupportActionBar().setIcon(R.drawable.sag);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

//        boolean havePermission = true;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(new String[]{CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
//                havePermission = false;
            }
            if (checkSelfPermission(RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
            }
        }
//        if (havePermission) {
//            onCameraPermissionGranted();
//        }

//        // 안드로이드 버전이 6.0 이상
//        if(Build.VERSION.SDK_INT >= 23){
//            //인터넷이나 녹음 권한이 없으면 권한 요청
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_DENIED
//                    || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED
//                    || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
//                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},PERMISSION);
//        }

        /*usb 카메라*/
//        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
//        private static final String ACTION_USB_PERMISSION =
//                "com.android.example.USB_PERMISSION";
//
//        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
//        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
//        registerReceiver(usbReceiver, filter);


        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_surface_view);
        editText = findViewById(R.id.textResult);
        imageView = findViewById(R.id.imageView);
        button = findViewById(R.id.recordBtn);
//        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(cvCameraViewListener);
        mRecognizer.setRecognitionListener(listener); // 리스너 설정
        try {
            int inputSize = 48;
            facialExpressionRecognition = new facialExpressionRecognition(getAssets(), CameraActivity.this, "model300.tflite", inputSize);
        }
        catch (IOException e) {
            e.printStackTrace();
            Log.d("CameraActivity", "Model is not loaded");
        }

        // RecognizerIntent 생성
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName()); // 여분의 키
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()); // 언어 설정
        }

        //감정 이모지 변경


        // 버튼 클릭 시 객체에 Context와 listener를 할당
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!recording) {   //녹음 시작
                    mRecognizer.startListening(intent); // 듣기 시작

                    recording = true;
                    button.setText("Stop");
                    // 화면 켜짐 유지
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


                    editText.setText("");
                    faceRecognitionResult.clear();
                    mThread = new BackgroundThread();
                    mThread.start();
                } else {  //이미 녹음 중이면 녹음 중지
                    mRecognizer.cancel();
                    StopRecord();

                    mThread.interrupt(); // stop UI update
                }
            }
        });
    }

    private void StopRecord() {
        recording = false;
        button.setText("Analyze Emotion");
        // 화면 켜짐 유지 끄기
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            // 말하기 시작할 준비가되면 호출
        }

        @Override
        public void onBeginningOfSpeech() {
            // 말하기 시작했을 때 호출
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // 입력받는 소리의 크기를 알려줌
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // 말을 시작하고 인식이 된 단어를 buffer에 담음
        }

        @Override
        public void onEndOfSpeech() {
            // 말하기를 중지하면 호출
        }

        @Override
        public void onError(int error) {
            // 네트워크 또는 인식 오류가 발생했을 때 호출
            String message;

            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "오디오 에러";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "퍼미션 없음";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "네트워크 에러";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "네트웍 타임아웃";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    Toast.makeText(getApplicationContext(), "인식할 수 없는 음성 또는 음성 입력 대기 중",Toast.LENGTH_SHORT).show();
                    mRecognizer.startListening(intent);
                    return;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RECOGNIZER 가 바쁨";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "서버가 이상함";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "말하는 시간초과";
                    break;
                case 12:
                    message = "인식기가 지원하지 않는 언어";
                    break;
                default:
                    message = "알 수 없는 오류임" + error;
                    break;
            }
            StopRecord();
            if(!message.equals(""))
                Toast.makeText(getApplicationContext(), "에러 발생 : " + message,Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            // 인식 결과가 준비되면 호출
            // 말을 하면 ArrayList에 단어를 넣고 textView에 단어를 이어줌
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String originText = editText.getText().toString();

            // 인식 결과
            String newText="";
            for(int i = 0; i < matches.size() ; i++){
                newText += matches.get(i);
            }

//            editText.setText(originText + newText + " ");	//기존의 text에 인식 결과를 이어붙임
            mRecognizer.startListening(intent);    //녹음버튼을 누를 때까지 계속 녹음해야 하므로 녹음 재개
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            // 부분 인식 결과를 사용할 수 있을 때 호출
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // 향후 이벤트를 추가하기 위해 예약
        }
    };

    private CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener = new CameraBridgeViewBase.CvCameraViewListener2() {
        @Override
        public void onCameraViewStarted(int width ,int height){
            mRgba=new Mat(height,width, CvType.CV_8UC4);
            mGray =new Mat(height,width,CvType.CV_8UC1);
        }

        @Override
        public void onCameraViewStopped() {

        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
            mRgba=inputFrame.rgba();
            mGray=inputFrame.gray();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mRgba = facialExpressionRecognition.recognizeImage(mRgba);
            }
            return mRgba;

        }
    };

    @Override
    protected void onStart() {
        super.onStart();
    }

    protected List<?extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()){
            //if load success
            Log.d(LOGTAG,"Opencv initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else{
            //if not loaded
            Log.d(LOGTAG,"Opencv is not loaded. try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,this,mLoaderCallback);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    // thread for update UI
    class BackgroundThread extends Thread {
        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        float emotion_v = facialExpressionRecognition.get_emotion_value();

                        if (emotion_v == 0.0) {
                            faceRecognitionResult.clear();
                        } else {
                            faceRecognitionResult.add(facialExpressionRecognition.get_emotion_value());

                            if (faceRecognitionResult.size() > 5) {
                                float result_average = 0;
                                for (int i = 0; i < faceRecognitionResult.size(); i++) {
                                    result_average += faceRecognitionResult.get(i);
                                }
                                result_average /= faceRecognitionResult.size();

                                editText.setText(editText.getText() + "\n" + facialExpressionRecognition.get_emotion_text(result_average) + " " + result_average);

                                faceRecognitionResult.clear();
                            }
                        }
                    }
                });

                SystemClock.sleep(200);
            }
            faceRecognitionResult.clear();
        }
    }
}