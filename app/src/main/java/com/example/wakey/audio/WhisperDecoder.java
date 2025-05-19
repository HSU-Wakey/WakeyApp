package com.example.wakey.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WhisperDecoder {
    private static final String TAG = "녹음디버그 Decoder";
    private static final String MODEL_NAME = "HfWhisperDecoder.tflite";
    private static final int VOCAB_SIZE = 51865;
    private static final int NUM_LAYERS = 12;
    private static final int MAX_SEQ_LEN = 224;
    private static final int[] EXPECTED_K_SELF_SHAPE = {NUM_LAYERS, 1, 64, MAX_SEQ_LEN};
    private static final int[] EXPECTED_V_SELF_SHAPE = {NUM_LAYERS, 1, MAX_SEQ_LEN, 64};
    private static final int[] EXPECTED_K_CROSS_SHAPE = {NUM_LAYERS, 1, 64, 1500};
    private static final int[] EXPECTED_V_CROSS_SHAPE = {NUM_LAYERS, 1, 1500, 64};

    private Interpreter tflite;
    private float[][][][][] kCacheCross; // [NUM_LAYERS, 1, 1, 64, 1500]
    private float[][][][][] vCacheCross; // [NUM_LAYERS, 1, 1, 1500, 64]
    private float[][][][][] kCacheSelf; // [NUM_LAYERS, 1, 1, 64, MAX_SEQ_LEN]
    private float[][][][][] vCacheSelf; // [NUM_LAYERS, 1, 1, MAX_SEQ_LEN, 64]
    private int logitsIndex = -1;
    private int[] kCacheSelfOutIndices;
    private int[] vCacheSelfOutIndices;

    public WhisperDecoder(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_NAME);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            tflite = new Interpreter(modelBuffer, options);

            kCacheSelfOutIndices = new int[NUM_LAYERS];
            vCacheSelfOutIndices = new int[NUM_LAYERS];
            Arrays.fill(kCacheSelfOutIndices, -1);
            Arrays.fill(vCacheSelfOutIndices, -1);

            // 입력 텐서 정보 로깅
            for (int i = 0; i < tflite.getInputTensorCount(); i++) {
                int[] shape = tflite.getInputTensor(i).shape();
                String name = tflite.getInputTensor(i).name();
                Log.d(TAG, "Input tensor " + i + ": name=" + name + ", shape=" + Arrays.toString(shape));
            }

            // 출력 텐서 매핑
            for (int i = 0; i < tflite.getOutputTensorCount(); i++) {
                int[] shape = tflite.getOutputTensor(i).shape();
                String name = tflite.getOutputTensor(i).name();
                Log.d(TAG, "Output tensor " + i + ": name=" + name + ", shape=" + Arrays.toString(shape));
                if (shape.length == 4 && shape[1] == VOCAB_SIZE) {
                    logitsIndex = i;
                    Log.d(TAG, "Found logits at index " + i);
                } else if (name.contains("k_cache_self") && shape.length == 4) {
                    int layer = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                    kCacheSelfOutIndices[layer] = i;
                    if (!Arrays.equals(shape, EXPECTED_K_SELF_SHAPE)) {
                        Log.w(TAG, "k_cache_self_" + layer + " shape mismatch: expected=" + Arrays.toString(EXPECTED_K_SELF_SHAPE) + ", got=" + Arrays.toString(shape));
                    }
                    Log.d(TAG, "Found k_cache_self_" + layer + " at index " + i);
                } else if (name.contains("v_cache_self") && shape.length == 4) {
                    int layer = Integer.parseInt(name.replaceAll("[^0-9]", ""));
                    vCacheSelfOutIndices[layer] = i;
                    if (!Arrays.equals(shape, EXPECTED_V_SELF_SHAPE)) {
                        Log.w(TAG, "v_cache_self_" + layer + " shape mismatch: expected=" + Arrays.toString(EXPECTED_V_SELF_SHAPE) + ", got=" + Arrays.toString(shape));
                    }
                    Log.d(TAG, "Found v_cache_self_" + layer + " at index " + i);
                }
            }

            if (logitsIndex == -1) {
                Log.e(TAG, "logitsIndex를 찾을 수 없습니다.");
                throw new IllegalStateException("Logits 출력 없음");
            }

            for (int i = 0; i < NUM_LAYERS; i++) {
                if (kCacheSelfOutIndices[i] == -1 || vCacheSelfOutIndices[i] == -1) {
                    Log.e(TAG, "kCacheSelf 또는 vCacheSelf 출력 인덱스 누락: layer=" + i);
                    throw new IllegalStateException("Self cache 인덱스 누락");
                }
            }

            // 캐시 초기화
            kCacheCross = new float[NUM_LAYERS][1][1][64][1500];
            vCacheCross = new float[NUM_LAYERS][1][1][1500][64];
            kCacheSelf = new float[NUM_LAYERS][1][1][64][MAX_SEQ_LEN];
            vCacheSelf = new float[NUM_LAYERS][1][1][MAX_SEQ_LEN][64];

            Log.d(TAG, "Decoder 모델 로드 성공, logitsIndex=" + logitsIndex);
            Log.d(TAG, "kCacheCross shape: [" + kCacheCross.length + ", " + kCacheCross[0].length + ", " + kCacheCross[0][0].length + ", " + kCacheCross[0][0][0].length + ", " + kCacheCross[0][0][0][0].length + "]");
            Log.d(TAG, "kCacheSelf shape: [" + kCacheSelf.length + ", " + kCacheSelf[0].length + ", " + kCacheSelf[0][0].length + ", " + kCacheSelf[0][0][0].length + ", " + kCacheSelf[0][0][0][0].length + "]");
        } catch (IOException e) {
            Log.e(TAG, "Decoder 모델 로딩 실패: " + e.getMessage(), e);
            throw new RuntimeException("Failed to load decoder model", e);
        }
    }

    public void initializeCache(float[][][][][] kCacheCross, float[][][][][] vCacheCross) {
        if (kCacheCross == null || vCacheCross == null ||
                kCacheCross.length != NUM_LAYERS || vCacheCross.length != NUM_LAYERS ||
                !Arrays.equals(new int[]{kCacheCross.length, kCacheCross[0].length, kCacheCross[0][0].length, kCacheCross[0][0][0].length, kCacheCross[0][0][0][0].length}, EXPECTED_K_CROSS_SHAPE) ||
                !Arrays.equals(new int[]{vCacheCross.length, vCacheCross[0].length, vCacheCross[0][0].length, vCacheCross[0][0][0].length, vCacheCross[0][0][0][0].length}, EXPECTED_V_CROSS_SHAPE)) {
            Log.e(TAG, "잘못된 캐시 형상: kCacheCross=" + (kCacheCross == null ? "null" : Arrays.toString(new int[]{kCacheCross.length, kCacheCross[0].length, kCacheCross[0][0].length, kCacheCross[0][0][0].length, kCacheCross[0][0][0][0].length})) +
                    ", vCacheCross=" + (vCacheCross == null ? "null" : Arrays.toString(new int[]{vCacheCross.length, vCacheCross[0].length, vCacheCross[0][0].length, vCacheCross[0][0][0].length, vCacheCross[0][0][0][0].length})));
            throw new IllegalArgumentException("잘못된 캐시 형상");
        }
        this.kCacheCross = kCacheCross;
        this.vCacheCross = vCacheCross;
        Log.d(TAG, "Cache 초기화 완료");
    }

    public Map<String, Object> run(float[][][] encoderOutput, int currentToken, int index) {
        if (index < 0 || index >= MAX_SEQ_LEN) {
            Log.e(TAG, "잘못된 index: " + index);
            throw new IllegalArgumentException("잘못된 index");
        }

        int[] logitsShape = tflite.getOutputTensor(logitsIndex).shape();
        float[][][][] logits = new float[logitsShape[0]][logitsShape[1]][logitsShape[2]][logitsShape[3]];
        float[][][][][] kCacheSelfOut = new float[NUM_LAYERS][1][1][64][MAX_SEQ_LEN];
        float[][][][][] vCacheSelfOut = new float[NUM_LAYERS][1][1][MAX_SEQ_LEN][64];

        // attention_mask 생성
        float[][][][] attentionMask = new float[1][1][1][MAX_SEQ_LEN];
        for (int i = 0; i < MAX_SEQ_LEN; i++) {
            attentionMask[0][0][0][i] = (i <= index) ? 0f : -100f;
        }

        // 입력 배열
        Object[] inputs = new Object[2 + 4 * NUM_LAYERS + 1];
        inputs[0] = new int[]{currentToken}; // input_ids: [1]
        inputs[1] = attentionMask; // attention_mask: [1, 1, 1, MAX_SEQ_LEN]
        for (int i = 0; i < NUM_LAYERS; i++) {
            inputs[2 + i] = kCacheSelf[i]; // k_cache_self_*_in
            inputs[2 + NUM_LAYERS + i] = vCacheSelf[i]; // v_cache_self_*_in
            inputs[2 + 2 * NUM_LAYERS + i] = kCacheCross[i]; // k_cache_cross_*_in
            inputs[2 + 3 * NUM_LAYERS + i] = vCacheCross[i]; // v_cache_cross_*_in
        }
        inputs[2 + 4 * NUM_LAYERS] = new int[]{index}; // position_ids: [1]

        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(logitsIndex, logits);
        for (int i = 0; i < NUM_LAYERS; i++) {
            outputs.put(kCacheSelfOutIndices[i], kCacheSelfOut[i]);
            outputs.put(vCacheSelfOutIndices[i], vCacheSelfOut[i]);
        }

        try {
            tflite.runForMultipleInputsOutputs(inputs, outputs);
            Log.d(TAG, "Decoder 추론 성공, logits shape: [" + logits.length + ", " + logits[0].length + ", " + logits[0][0].length + ", " + logits[0][0][0].length + "]");
        } catch (Exception e) {
            Log.e(TAG, "Decoder 추론 실패: " + e.getMessage(), e);
            throw new RuntimeException("Decoder 추론 실패", e);
        }

        for (int i = 0; i < NUM_LAYERS; i++) {
            kCacheSelf[i] = kCacheSelfOut[i];
            vCacheSelf[i] = vCacheSelfOut[i];
        }

        Map<String, Object> result = new HashMap<>();
        result.put("logits", logits);
        return result;
    }

    public void resetCache() {
        kCacheCross = new float[NUM_LAYERS][1][1][64][1500];
        vCacheCross = new float[NUM_LAYERS][1][1][1500][64];
        kCacheSelf = new float[NUM_LAYERS][1][1][64][MAX_SEQ_LEN];
        vCacheSelf = new float[NUM_LAYERS][1][1][MAX_SEQ_LEN][64];
        Log.d(TAG, "Cache 리셋 완료");
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
            Log.d(TAG, "Decoder 리소스 해제 완료");
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