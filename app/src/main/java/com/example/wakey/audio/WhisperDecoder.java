package com.example.wakey.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class WhisperDecoder {
    private static final String TAG = "WhisperDecoder";
    private static final String MODEL_NAME = "whisper_small_v2-hfwhisperdecoder.tflite";

    private Interpreter tflite;

    public WhisperDecoder(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_NAME);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            tflite = new Interpreter(modelBuffer, options);
        } catch (IOException e) {
            Log.e(TAG, "Decoder 모델 로딩 실패: " + e.getMessage());
        }
    }

    // decoder 추론: encoder output + token sequence → 다음 토큰 확률
    public float[][] run(float[][][] encoderOutput, int[] tokens) {
        // 입력: [1, 3000, 512], [1, N]
        // 출력: [1, vocab_size] (예: 51864)
        float[][] logits = new float[1][51864]; // Whisper vocab size 기준
        Object[] inputs = {encoderOutput, new int[][]{tokens}};
        tflite.runForMultipleInputsOutputs(inputs, java.util.Collections.singletonMap(0, logits));
        return logits;
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
