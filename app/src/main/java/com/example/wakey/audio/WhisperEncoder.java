package com.example.wakey.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WhisperEncoder {
    private static final String TAG = "녹음디버그 Encoder";
    private static final String MODEL_NAME = "HfWhisperEncoder.tflite";
    private static final int NUM_LAYERS = 12;
    private static final int[] EXPECTED_K_SHAPE = {NUM_LAYERS, 1, 64, 1500};
    private static final int[] EXPECTED_V_SHAPE = {NUM_LAYERS, 1, 1500, 64};
    private static final int[] EXPECTED_INPUT_SHAPE = {1, 80, 3000}; // 추가: Mel 입력 형상

    private Interpreter tflite;
    private int embeddingIndex = -1;
    private int[] kCacheCrossIndices;
    private int[] vCacheCrossIndices;

    public WhisperEncoder(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_NAME);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            tflite = new Interpreter(modelBuffer, options);

            kCacheCrossIndices = new int[NUM_LAYERS];
            vCacheCrossIndices = new int[NUM_LAYERS];
            Arrays.fill(kCacheCrossIndices, -1);
            Arrays.fill(vCacheCrossIndices, -1);

            // 입력 텐서 검증
            if (tflite.getInputTensorCount() < 1) {
                throw new IllegalStateException("Encoder 입력 텐서 없음");
            }
            int[] inputShape = tflite.getInputTensor(0).shape();
            if (!Arrays.equals(inputShape, EXPECTED_INPUT_SHAPE)) {
                Log.w(TAG, "입력 텐서 형상 불일치: expected=" + Arrays.toString(EXPECTED_INPUT_SHAPE) + ", got=" + Arrays.toString(inputShape));
            }
            Log.d(TAG, "Input tensor 0: name=" + tflite.getInputTensor(0).name() + ", shape=" + Arrays.toString(inputShape));

            // 출력 텐서 매핑
            for (int i = 0; i < tflite.getOutputTensorCount(); i++) {
                Tensor tensor = tflite.getOutputTensor(i);
                String name = tensor.name();
                int[] shape = tensor.shape();
                Log.d(TAG, "Output tensor " + i + ": name=" + name + ", shape=" + Arrays.toString(shape));

                if (shape.length == 3 && shape[0] == 1 && shape[2] == 512) {
                    embeddingIndex = i;
                    Log.d(TAG, "Found embedding tensor at index " + i);
                } else if (name.contains("k_cache_cross") && shape.length == 4) {
                    int layer = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                    kCacheCrossIndices[layer] = i;
                    if (!Arrays.equals(shape, EXPECTED_K_SHAPE)) {
                        Log.w(TAG, "k_cache_cross_" + layer + " shape mismatch: expected=" + Arrays.toString(EXPECTED_K_SHAPE) + ", got=" + Arrays.toString(shape));
                    }
                    Log.d(TAG, "Found k_cache_cross_" + layer + " at index " + i);
                } else if (name.contains("v_cache_cross") && shape.length == 4) {
                    int layer = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                    vCacheCrossIndices[layer] = i;
                    if (!Arrays.equals(shape, EXPECTED_V_SHAPE)) {
                        Log.w(TAG, "v_cache_cross_" + layer + " shape mismatch: expected=" + Arrays.toString(EXPECTED_V_SHAPE) + ", got=" + Arrays.toString(shape));
                    }
                    Log.d(TAG, "Found v_cache_cross_" + layer + " at index " + i);
                }
            }

            for (int i = 0; i < NUM_LAYERS; i++) {
                if (kCacheCrossIndices[i] == -1 || vCacheCrossIndices[i] == -1) {
                    Log.e(TAG, "kCacheCross 또는 vCacheCross 인덱스 누락: layer=" + i);
                    throw new IllegalStateException("Cache 인덱스 누락");
                }
            }

            Log.d(TAG, "Encoder 모델 로드 성공, embeddingIndex=" + embeddingIndex + (embeddingIndex == -1 ? " (KV 캐시만 사용)" : ""));
        } catch (IOException e) {
            Log.e(TAG, "모델 로드 실패: " + e.getMessage(), e);
            throw new RuntimeException("Failed to load encoder model", e);
        }
    }

    public Map<String, Object> runInference(float[][][] melInput) {
        if (melInput == null || melInput.length != EXPECTED_INPUT_SHAPE[0] ||
                melInput[0].length != EXPECTED_INPUT_SHAPE[1] || melInput[0][0].length != EXPECTED_INPUT_SHAPE[2]) {
            Log.e(TAG, "잘못된 Mel 입력 형상: " + (melInput == null ? "null" : Arrays.toString(new int[]{melInput.length, melInput[0].length, melInput[0][0].length})));
            throw new IllegalArgumentException("잘못된 Mel 입력 형상");
        }

        float[][][][][] kCacheCross = new float[NUM_LAYERS][1][1][64][1500];
        float[][][][][] vCacheCross = new float[NUM_LAYERS][1][1][1500][64];
        float[][][] embedding = null;

        Map<Integer, Object> outputs = new HashMap<>();
        for (int i = 0; i < NUM_LAYERS; i++) {
            outputs.put(kCacheCrossIndices[i], kCacheCross[i]);
            outputs.put(vCacheCrossIndices[i], vCacheCross[i]);
        }

        if (embeddingIndex != -1) {
            int[] embeddingShape = tflite.getOutputTensor(embeddingIndex).shape();
            embedding = new float[embeddingShape[0]][embeddingShape[1]][embeddingShape[2]];
            outputs.put(embeddingIndex, embedding);
        }

        Object[] inputs = {melInput};
        try {
            tflite.runForMultipleInputsOutputs(inputs, outputs);
            Log.d(TAG, "Encoder 추론 성공");
            Log.d(TAG, "kCacheCross[0] shape: [" + kCacheCross[0].length + ", " + kCacheCross[0][0].length + ", " + kCacheCross[0][0][0].length + ", " + kCacheCross[0][0][0][0].length + "]");
            Log.d(TAG, "vCacheCross[0] shape: [" + vCacheCross[0].length + ", " + vCacheCross[0][0].length + ", " + vCacheCross[0][0][0].length + ", " + vCacheCross[0][0][0][0].length + "]");
            Log.d(TAG, "embedding: " + (embedding != null ? Arrays.toString(new int[]{embedding.length, embedding[0].length, embedding[0][0].length}) : "null"));

            Map<String, Object> result = new HashMap<>();
            result.put("kCacheCross", kCacheCross);
            result.put("vCacheCross", vCacheCross);
            result.put("embedding", embedding);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Encoder 추론 실패: " + e.getMessage(), e);
            throw new RuntimeException("Encoder 추론 실패", e);
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
            Log.d(TAG, "Encoder 리소스 해제 완료");
        }
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