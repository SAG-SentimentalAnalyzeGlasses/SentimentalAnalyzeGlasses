package com.example.sentimentanalyzer;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private long backKeyPressedTime = 0;
    private static String LOGTAG = "OpenCV_Log";
    private facialExpressionRecognition facialExpressionRecognition;
    private Mat mRgba;
    private Mat mGray;
    private boolean recording = false;

    private Intent intent;
    private CameraBridgeViewBase mOpenCvCameraView;
    //    private EditText editText;
    private TextView textOutput;
    private TextView textError;
    private Button button;
    private SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(CameraActivity.this); // 새 SpeechRecognizer 를 만드는 팩토리 메서드
    private BackgroundThread mThread;
    private VoiceThread sThread;
    private boolean isReadyToSend = false;
    private String currentVoiceInput;
    private float voice_v = 0;
    private boolean isVoiceValueUpdated = false;

    //Graph
    private LineChart chart;

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
//    private static final String ACTION_USB_PERMISSION =
//            "com.android.example.USB_PERMISSION";
//    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
//
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (ACTION_USB_PERMISSION.equals(action)) {
//                synchronized (this) {
//                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//
//                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
//                        if (device != null) {
//                            //call method to set up device communication
//                        }
//                    } else {
//                        Log.v(LOGTAG, "permission denied for device " + device);
//                    }
//                }
//            }
//        }
//    };


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.v(LOGTAG, "OpenCV Loaded");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }

        /*usb 카메라*/
//        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
//        private static final String ACTION_USB_PERMISSION =
//                "com.android.example.USB_PERMISSION";
//
//        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
//        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
//        registerReceiver(usbReceiver, filter);


        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_surface_view);
        textOutput = findViewById(R.id.textOutput);
        textError = findViewById(R.id.textError);
        button = findViewById(R.id.recordBtn);
//        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(cvCameraViewListener);
        mRecognizer.setRecognitionListener(listener); // 리스너 설정
        try {
            int inputSize = 48;
            facialExpressionRecognition = new facialExpressionRecognition(getAssets(), CameraActivity.this, "model.tflite", inputSize);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("CameraActivity", "Model is not loaded");
        }

        // RecognizerIntent 생성
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName()); // 여분의 키
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()); // 언어 설정
        }

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

                    chart.clear();
//                    editText.setText("");
                    textOutput.setText("");
                    currentVoiceInput = "";
                    mThread = new BackgroundThread();
                    sThread = new VoiceThread();
                    mThread.start();
                    sThread.start();
                } else {  //이미 녹음 중이면 녹음 중지
                    StopRecord();

                    textError.setText("");
                }
            }
        });

        chart = (LineChart) findViewById(R.id.LineChart);
        chart.setDrawGridBackground(true);
        chart.setBackgroundColor(getResources().getColor(R.color.sag_green));
        chart.setGridBackgroundColor(getResources().getColor(R.color.sag_green));
        // description text
        chart.getDescription().setEnabled(true);
        Description des = chart.getDescription();
        des.setEnabled(true);
        des.setText("SAG");
        des.setTextSize(5f);
        des.setTextColor(getResources().getColor(R.color.sag_background));


        // touch gestures (false-비활성화)
        chart.setTouchEnabled(false);

        // scaling and dragging (false-비활성화)
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);

        //auto scale
        chart.setAutoScaleMinMaxEnabled(false);

        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(true);

        //X축
        XAxis x1 = chart.getXAxis();
        x1.setDrawGridLines(false);
        x1.setDrawAxisLine(false);
        x1.setTextColor(getResources().getColor(R.color.sag_background));
        x1.setTextSize(1f);
        x1.setAvoidFirstLastClipping(true);

        x1.setEnabled(true);

        //Legend
        Legend l = chart.getLegend();
        l.setEnabled(true);
        l.setFormSize(10f); // set the size of the legend forms/shapes
        l.setTextSize(12f);
        l.setTextColor(getResources().getColor(R.color.sag_background));

        //Y축
        YAxis leftAxis = chart.getAxisLeft();
//        leftAxis.setValueFormatter(new MyYAxisValueFormatter());
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value == 0.0f) {
                    return "Angry😠";
                } else if (value >= 1.0f && value <= 1.0f) {
                    return "Disgust😫";
                } else if (value >= 2.0f && value <= 2.0f) {
                    return "Fear😨";
                } else if (value >= 3.0f && value <= 3.0f) {
                    return "Happy😄";
                } else if (value >= 4.0f && value <= 4.0f) {
                    return "Neutral😐";
                } else if (value >= 5.0f && value <= 5.0f) {
                    return "Sad😢";
                } else if (value >= 6.0f && value <= 6.0f) {
                    return "Surprise😲";
                } else {
                    return Float.toString(value);
                }
            }
        });
        leftAxis.setGranularity(1.0f);
        leftAxis.setEnabled(true);
        leftAxis.setTextColor(getResources().getColor(R.color.sag_background));
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridLineWidth(0.3f);
        leftAxis.setGridColor(getResources().getColor(R.color.sag_background));
        leftAxis.setAxisMaximum(6.0f);
        leftAxis.setAxisMinimum(0.0f);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        // don't forget to refresh the drawing
        chart.invalidate();

    }

    private void addEntry(double num1, double num2) {
        LineData data = chart.getData();
        if (data == null) {
            data = new LineData();
            chart.setData(data);
        }

        ILineDataSet set = data.getDataSetByIndex(0);
        ILineDataSet set1 = data.getDataSetByIndex(1);

        if (set == null) {
            set = createSet(getResources().getColor(R.color.sag_background), "Video");
            data.addDataSet(set);
        }
        if (set1 == null) {
            set1 = createSet(getResources().getColor(R.color.sag_white), "Video + Voice");
            data.addDataSet(set1);
        }

        data.addEntry(new Entry((float)set.getEntryCount(), (float)num1), 0);
        if(num2 != -1) {
            data.addEntry(new Entry((float) set.getEntryCount()-1, (float) num2), 1);
        }
        data.notifyDataChanged();

        // let the chart know it's data has changed
        chart.notifyDataSetChanged();

        chart.setVisibleXRangeMaximum(30);
        // this automatically refreshes the chart (calls invalidate())
        chart.moveViewTo(data.getEntryCount(), 50f, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet createSet(int color, String name) {
        LineDataSet set = new LineDataSet(null, name);
//        set.set
        set.setValueTextColor(getResources().getColor(R.color.sag_white));
        int color1 = set.getHighLightColor();
        Log.v(LOGTAG, "getHighLightColor: " + color1);
        set.setHighLightColor(getResources().getColor(R.color.sag_white));
        set.setLineWidth(2f);
        set.setCircleRadius(2.5f);
        set.setDrawCircleHole(true);
        set.setCircleColor(color);
        set.setDrawValues(false);
        set.setColor(color);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        return set;
    }
    @SuppressLint("ResourceAsColor")

    private void StopRecord() {
//        socket.close();
        mRecognizer.cancel();
        recording = false;

        if (mThread != null) {
            mThread.interrupt(); // stop UI update
        }
        if (sThread != null) {
            sThread.interrupt();
        }
        button.setText("Start Analyze Emotion");
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
//                    Toast.makeText(getApplicationContext(), "인식할 수 없는 음성 또는 음성 입력 대기 중", Toast.LENGTH_SHORT).show();
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
            if (!message.equals(""))
                Toast.makeText(getApplicationContext(), "에러 발생 : " + message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            // 인식 결과가 준비되면 호출
            // 말을 하면 ArrayList에 단어를 넣고 textView에 단어를 이어줌
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            // 인식 결과
            for (int i = 0; i < matches.size(); i++) {
                currentVoiceInput += matches.get(i);
            }

            if (!currentVoiceInput.equals("")) {
                currentVoiceInput = currentVoiceInput.replace("\n", "");
                isReadyToSend = true;
//                editText.setText(currentVoiceInput);
            } else {
//                editText.setText("");
            }
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
        public void onCameraViewStarted(int width, int height) {
            mRgba = new Mat(height, width, CvType.CV_8UC4);
            mGray = new Mat(height, width, CvType.CV_8UC1);
        }

        @Override
        public void onCameraViewStopped() {

        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            mRgba = inputFrame.rgba();
            mGray = inputFrame.gray();

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

    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase : cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            //if load success
            Log.d(LOGTAG, "Opencv initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            //if not loaded
            Log.d(LOGTAG, "Opencv is not loaded. try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
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

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() > backKeyPressedTime + 2000) {
            backKeyPressedTime = System.currentTimeMillis();
            Toast.makeText(this, "\'뒤로\' 버튼 한 번 더 누르시면 종료됩니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (System.currentTimeMillis() <= backKeyPressedTime + 2000) {
            StopRecord();
            finish();
        }
    }


    // thread for UI update
    class BackgroundThread extends Thread {
        private int cycle;
        private int[] countEmotion;

        @Override
        public void run() {
            cycle = 0;
            countEmotion = new int[7];

            while (!Thread.currentThread().isInterrupted()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int emotion_v = facialExpressionRecognition.get_emotion_value();

                        if (emotion_v == -1) {
                            textError.setText("얼굴을 인식할 수 없거나 2개 이상의 얼굴 존재");
                            cycle = 0;
                            countEmotion = new int[7];
                        } else {
                            textError.setText("");

                            countEmotion[emotion_v]++;

                            if (++cycle > 4) {
//                                float result_average = 0;
//                                float result_averageper4s = 2f;
//                                for (int i = 0; i < faceRecognitionResult.size(); i++) {
//                                    result_average += faceRecognitionResult.get(i);
//                                }
//                                result_average /= faceRecognitionResult.size();

                                int maxEmotionIndex = 0;
                                for (int i = 0; i < countEmotion.length; i++) {
                                    maxEmotionIndex = countEmotion[i] > countEmotion[maxEmotionIndex] ? i : maxEmotionIndex;
                                }
                                int voiceSentimentIndex = -1;
                                if (isVoiceValueUpdated) {
                                    isVoiceValueUpdated = false;

                                    if (voice_v < 0.43068436) {
                                        if (maxEmotionIndex == 0 | maxEmotionIndex == 3) {
                                            if (voice_v < 0.4) {
                                                voiceSentimentIndex = 5;
                                            } else {
                                                voiceSentimentIndex = 6;
                                            }
                                        } else if (maxEmotionIndex == 1 | maxEmotionIndex == 2 | maxEmotionIndex == 4 | maxEmotionIndex == 5) {
                                            if (voice_v < 0.4) {
                                                voiceSentimentIndex = 5;
                                            } else {
                                                voiceSentimentIndex = Math.round(facialExpressionRecognition.get_emotion_value());
                                            }
                                        } else {
                                            if (voice_v < 0.4) {
                                                voiceSentimentIndex = Math.round(facialExpressionRecognition.get_emotion_value());
                                            } else {
                                                voiceSentimentIndex = 6;
                                            }
                                        }
                                    } else {
                                        voiceSentimentIndex = maxEmotionIndex;
                                    }
                                }
                                addEntry(maxEmotionIndex, voiceSentimentIndex);

                                if (voiceSentimentIndex == -1) {
                                    textOutput.setText("Current image: " + facialExpressionRecognition.get_emotion_text(maxEmotionIndex) + " (Probability: " + facialExpressionRecognition.get_emotion_probability() + ")");
                                } else {
                                    textOutput.setText("Current image: " + facialExpressionRecognition.get_emotion_text(maxEmotionIndex) + ", voice: " + facialExpressionRecognition.get_emotion_text(voiceSentimentIndex));
                                }
                                cycle = 0;
                                countEmotion = new int[7];
                            }
                        }
                    }
                });

                SystemClock.sleep(200);
            }
        }
    }

    // thread for voice input
    class VoiceThread extends Thread {
        private Socket socket;

        @Override
        public void run() {
            try {
                socket = new Socket("192.168.0.2", 8888);

                while (!Thread.currentThread().isInterrupted()) {
                    if (isReadyToSend) {
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out.writeUTF(currentVoiceInput);
                        String text_v = in.readLine();

                        voice_v = Float.parseFloat(text_v);
                        isVoiceValueUpdated = true;

                        currentVoiceInput = "";
                        isReadyToSend = false;
                    }
                }
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            isReadyToSend = false;
        }
    }
}

