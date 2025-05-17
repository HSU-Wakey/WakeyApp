package com.example.wakey.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class WhisperEncoder {
    private static final String TAG = "WhisperEncoder";
    private static final String MODEL_NAME = "whisper_small_v2-hfwhisperencoder.tflite";

    private Interpreter tflite;

    public WhisperEncoder(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_NAME);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // 디바이스 성능에 따라 조절
            tflite = new Interpreter(modelBuffer, options);
        } catch (IOException e) {
            Log.e(TAG, "모델 로드 실패: " + e.getMessage());
        }
    }

    // Whisper Encoder 추론
    public float[][][] runInference(float[][][] melInput) {
        float[][][] output = new float[1][3000][512];  // Qualcomm 모델 기준
        tflite.run(melInput, output);
        return output;
    }

    private MappedByteBuffer loadModelFile(Context context, String filename) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(filename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
