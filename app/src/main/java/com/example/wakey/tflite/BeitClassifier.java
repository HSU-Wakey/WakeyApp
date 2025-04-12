package com.example.wakey.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.DataType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BeitClassifier {
    private static final String MODEL_PATH = "beit-snapdragon_8_gen_3.tflite"; // Qualcomm AI Hub 모델
    private static final String LABELS_PATH = "labels.txt"; // ImageNet 레이블
    private static final int IMAGE_SIZE = 224; // 입력 크기
    private static final int NUM_CLASSES = 1000; // 클래스 수
    private static final float[] MEAN_RGB = {0.485f, 0.456f, 0.406f}; // ImageNet 정규화 평균
    private static final float[] STD_RGB = {0.229f, 0.224f, 0.225f}; // ImageNet 정규화 표준편차
    private Interpreter tflite;
    private List<String> labels;

    public BeitClassifier(Context context) throws IOException {
        // 모델 로드
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, MODEL_PATH);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // 멀티스레딩
        options.setUseNNAPI(true); // NPU 가속
        tflite = new Interpreter(tfliteModel, options);

        // 디바이스 정보 로그
        String deviceInfo = "디바이스: " + Build.MODEL + ", 제조사: " + Build.MANUFACTURER +
                ", Android: " + Build.VERSION.RELEASE + ", NNAPI 지원: " + (Build.VERSION.SDK_INT >= 27);
        if (Build.MODEL.contains("sdk_gphone") || Build.MODEL.contains("emulator")) {
            deviceInfo += " (⚠️ 에뮬레이터: NPU 가속 불가, 성능 저하 예상)";
        }
        Log.d("BeitClassifier", "📱 " + deviceInfo);

        // 레이블 로드
        labels = FileUtil.loadLabels(context, LABELS_PATH);
        if (labels.size() != NUM_CLASSES) {
            throw new IOException("레이블 파일 크기 불일치: 기대 " + NUM_CLASSES + ", 실제 " + labels.size());
        }
        Log.d("BeitClassifier", "✅ BEiT 모델 및 레이블 로드 완료 (Qualcomm AI Hub TFLite)");
    }

    public List<Pair<String, Float>> classifyImage(Bitmap bitmap) {
        long startTime = System.nanoTime();
        Log.d("BeitClassifier", "📸 입력 이미지 크기: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        // 입력 검증
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("유효하지 않은 비트맵");
        }

        // 이미지 리사이징
        long preprocessStart = System.nanoTime();
        Bitmap rgbBitmap = bitmap.copy(Bitmap.Config.RGB_565, false);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(rgbBitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        rgbBitmap.recycle();
        Log.d("BeitClassifier", "🔄 리사이즈된 이미지 크기: " + resizedBitmap.getWidth() + "x" + resizedBitmap.getHeight());

        // 입력 버퍼
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        // ImageNet 정규화
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            inputBuffer.putFloat(((Color.red(pixel) / 255.0f) - MEAN_RGB[0]) / STD_RGB[0]);
            inputBuffer.putFloat(((Color.green(pixel) / 255.0f) - MEAN_RGB[1]) / STD_RGB[1]);
            inputBuffer.putFloat(((Color.blue(pixel) / 255.0f) - MEAN_RGB[2]) / STD_RGB[2]);
        }
        Log.d("BeitClassifier", "✅ 전처리 완료 (소요 시간: " + ((System.nanoTime() - preprocessStart) / 1_000_000.0) + "ms)");

        // 출력 버퍼
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, NUM_CLASSES}, DataType.FLOAT32);
        long inferenceStart = System.nanoTime();
        tflite.run(inputBuffer, outputBuffer.getBuffer().rewind());
        long inferenceEnd = System.nanoTime();
        Log.d("BeitClassifier", "🚀 추론 완료 (소요 시간: " + ((inferenceEnd - inferenceStart) / 1_000_000.0) + "ms)");

        // 출력 처리
        float[] logits = outputBuffer.getFloatArray();

        // 로짓 디버깅
        float maxLogit = Float.NEGATIVE_INFINITY;
        float minLogit = Float.POSITIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) maxLogit = logit;
            if (logit < minLogit) minLogit = logit;
        }
        Log.d("BeitClassifier", "🔍 로짓 범위: 최소 " + minLogit + ", 최대 " + maxLogit);

        // Softmax 계산
        float[] probabilities = new float[NUM_CLASSES];
        float sumExp = 0;
        for (int i = 0; i < NUM_CLASSES; i++) {
            float expValue = (float) Math.exp(logits[i] - maxLogit);
            probabilities[i] = expValue;
            sumExp += expValue;
        }
        Log.d("BeitClassifier", "🔢 Softmax sumExp: " + sumExp);
        for (int i = 0; i < NUM_CLASSES; i++) {
            probabilities[i] = (probabilities[i] / sumExp) * 100; // 퍼센트로 변환
        }

        // 확률 디버깅 (상위 5개 미리 확인)
        for (int i = 0; i < Math.min(5, NUM_CLASSES); i++) {
            Log.d("BeitClassifier", "🔎 Softmax 출력 [" + i + "]: " + probabilities[i] + "%");
        }

        // 결과 정렬
        List<Pair<String, Float>> results = new ArrayList<>();
        for (int i = 0; i < NUM_CLASSES; i++) {
            results.add(new Pair<>(labels.get(i), probabilities[i]));
        }
        Collections.sort(results, (o1, o2) -> Float.compare(o2.second, o1.second));

        // 상위 5개 결과 로깅
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            Log.d("BeitClassifier", "🔥 상위 [" + i + "]: " + results.get(i).first + " - " + results.get(i).second + "%");
        }

        long endTime = System.nanoTime();
        Log.d("BeitClassifier", "⏱️ 총 추론 시간: " + ((endTime - startTime) / 1_000_000.0) + "ms");

        // 메모리 해제
        resizedBitmap.recycle();

        return results.subList(0, Math.min(5, results.size()));
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
            Log.d("BeitClassifier", "🛑 Interpreter 종료");
        }
    }
}