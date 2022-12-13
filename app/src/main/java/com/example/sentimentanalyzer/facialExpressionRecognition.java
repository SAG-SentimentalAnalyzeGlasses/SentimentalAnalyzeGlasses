package com.example.sentimentanalyzer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class facialExpressionRecognition {
    private Interpreter interpreter;
    private int INPUT_SIZE;
    private int height = 0;
    private int width = 0;
    private GpuDelegate gpuDelegate = null;
    private CascadeClassifier cascadeClassifier;
    private float emotion_v;

    facialExpressionRecognition(AssetManager assetManager, Context context, String modelPath, int inputSize) throws IOException {
        INPUT_SIZE = inputSize;
        Interpreter.Options options = new Interpreter.Options();
        gpuDelegate = new GpuDelegate();
        options.addDelegate(gpuDelegate);
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);

        Log.d("facial_Expression:", "Model is loaded");

        try {
            InputStream is = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int byteRead;

            while ((byteRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, byteRead);
            }

            is.close();
            os.close();
            cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (cascadeClassifier.empty())
            {
                // 자바 디텍터 생성에 실패했다면 로그를 기록하고 null을 대입
                Log.e("cascadeClassifier:", "Failed to load cascade classifier");
                cascadeClassifier = null;
            }
            else
            {
                // 자바 디텍터 생성 성공 시 로그를 기록
                Log.i("cascadeClassifier:", "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }
            Log.d("facial_Expression", "Classifier is loaded");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public Mat recognizeImage(Mat mat_image) {
        // rotate it by 90 degree for proper prediction
        Core.flip(mat_image.t(), mat_image, 1);

        Mat grayscaleImage = new Mat();
        Imgproc.cvtColor(mat_image, grayscaleImage, Imgproc.COLOR_RGBA2GRAY);

        height = grayscaleImage.height();
        width = grayscaleImage.width();

        int absoluteFaceSize = (int)(height*0.1);

        MatOfRect faces = new MatOfRect();
        if(cascadeClassifier != null) {
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2, new Size(absoluteFaceSize, absoluteFaceSize), new Size());
        }

        Rect[] faceArray = faces.toArray();
        emotion_v = 0;

        for (int i = 0; i < faceArray.length; i++) {
            Imgproc.rectangle(mat_image, faceArray[i].tl(), faceArray[i].br(), new Scalar(0, 255, 0, 255), 2);
            Rect roi = new Rect((int)faceArray[i].tl().x, (int)faceArray[i].tl().y,
                    (int)faceArray[i].br().x - (int)faceArray[i].tl().x,
                    (int)faceArray[i].br().y - (int)faceArray[i].tl().y);
            Mat cropped_rgba = new Mat(mat_image, roi);

            Bitmap bitmap = Bitmap.createBitmap(cropped_rgba.cols(), cropped_rgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgba, bitmap);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, false);
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);

            float[][] emotion = new float[1][1];
            interpreter.run(byteBuffer, emotion);

            if (faceArray.length == 1) {
                emotion_v = (float)Array.get(Array.get(emotion, 0), 0);
            }
            Log.d("facial_expression", "Output: " + emotion_v);
//            String emotion_s = get_emotion_text(emotion_v);
//            Imgproc.putText(mat_image, emotion_s  + " (" + emotion_v + ")",
//                    new Point((int)faceArray[i].tl().x + 10, (int)faceArray[i].tl().y - 20), 1, 2, new Scalar(0, 0, 255, 150), 2);


            // 이미지 추가 예시)  img = cv2.add(img1, img2)
//            String imgpath = "src/main/res/drawable/soso.png";
//            Bitmap bm = BitmapFactory.decodeFile(imgpath);
//            Mat emotion_result = new Mat();
//            Utils.bitmapToMat(bm, emotion_result);




//            Core.add(mat_image, emotion_result, mat_image);

        }

        // rotaate mat_image -90 degree
        Core.flip(mat_image.t(), mat_image, 0);

        return mat_image;
    }

    public String get_emotion_text(float emotion_v) {
        String val = "";
        if (emotion_v >= 0 & emotion_v < 1.5) {
            val = "Surprise";
        } else if (emotion_v >= 0.5 & emotion_v < 1.5) {
            val = "Fear";
        } else if (emotion_v >= 1.5 & emotion_v < 2.5) {
            val = "Angry";
        } else if (emotion_v >= 2.5 & emotion_v < 3.5) {
            val = "Neutral";
        } else if (emotion_v >= 3.5 & emotion_v < 4.5) {
            val = "Sad";
        } else if (emotion_v >= 4.5 & emotion_v < 5.5) {
            val = "Disgust";
        } else {
            val = "Happy";
        }
        return val;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {
        ByteBuffer byteBuffer;
        int size_image = INPUT_SIZE;

        byteBuffer = ByteBuffer.allocateDirect(4*1*size_image*size_image*3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[size_image*size_image];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        int pixel = 0;
        for(int i = 0; i < size_image; ++i) {
            for(int j = 0; j < size_image; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat((((val>>16)&0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)&0xFF))/255.0f);
                byteBuffer.putFloat(((val&0xFF))/255.0f);
            }
        }
        return byteBuffer;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor assetFileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = assetFileDescriptor.getStartOffset();
        long declaredLength = assetFileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float get_emotion_value() {
        return emotion_v;
    }

    public String get_emotion_text() {
        return get_emotion_text(emotion_v);
    }
}
