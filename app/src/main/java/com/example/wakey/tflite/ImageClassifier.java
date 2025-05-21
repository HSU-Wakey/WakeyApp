package com.example.wakey.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageClassifier {
    private static final String TAG = "ImageClassifier";
    private static final String MODEL_PATH = "mobilenet_v3_large_quantized-snapdragon_8_gen_3.tflite";
    private static final int IMAGE_SIZE = 224;
    private static final int NUM_CLASSES = 1000;

    private final Interpreter tflite;
    private final List<String> labels;
    private final boolean isQuantized;

    public ImageClassifier(Context context) throws IOException {
        try {
            Log.d(TAG, "ImageClassifier 초기화 시작");

            // CPU 기반 인터프리터 옵션 설정
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // CPU 스레드 수 설정
            Log.d(TAG, "CPU 사용: 4 스레드");

            // 모델 파일 로드
            MappedByteBuffer tfliteModel;
            try {
                tfliteModel = FileUtil.loadMappedFile(context, MODEL_PATH);
                Log.d(TAG, "모델 파일 로드 성공: " + MODEL_PATH);
            } catch (IOException e) {
                Log.e(TAG, "모델 파일 로드 실패: " + e.getMessage(), e);
                throw e;
            }

            // 인터프리터 생성
            tflite = new Interpreter(tfliteModel, options);

            // 레이블 로드
            try {
                labels = FileUtil.loadLabels(context, "labels.txt");
                Log.d(TAG, "레이블 파일 로드 성공: " + labels.size() + "개 레이블");
            } catch (IOException e) {
                Log.e(TAG, "레이블 파일 로드 실패: " + e.getMessage(), e);
                throw e;
            }

            // 입력 텐서 정보 확인
            isQuantized = tflite.getInputTensor(0).dataType() == org.tensorflow.lite.DataType.UINT8;

            Log.d(TAG, "양자화 모델 여부: " + isQuantized);
            Log.d(TAG, "ImageClassifier 초기화 완료");

        } catch (Exception e) {
            Log.e(TAG, "ImageClassifier 초기화 중 예외 발생", e);
            close();
            throw e;
        }
    }

    public List<Pair<String, Float>> classifyImage(Bitmap bitmap) {
        Log.d("ImageClassifier", "📸 Selected Image Size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        Log.d("ImageClassifier", "🔄 Resized Image Size: " + resizedBitmap.getWidth() + "x" + resizedBitmap.getHeight());

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int pixel = resizedBitmap.getPixel(x, y);
                inputBuffer.put((byte) Color.red(pixel));
                inputBuffer.put((byte) Color.green(pixel));
                inputBuffer.put((byte) Color.blue(pixel));
            }
        }
        Log.d("ImageClassifier", "✅ Image processed and converted to ByteBuffer");

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, NUM_CLASSES}, DataType.UINT8);
        tflite.run(inputBuffer, outputBuffer.getBuffer());
        Log.d("ImageClassifier", "🚀 Model inference completed");

        float outputScale = tflite.getOutputTensor(0).quantizationParams().getScale();
        int outputZeroPoint = tflite.getOutputTensor(0).quantizationParams().getZeroPoint();
        Log.d("ImageClassifier", "🔍 Output Scale: " + outputScale + ", Zero Point: " + outputZeroPoint);

        int[] quantizedOutput = outputBuffer.getIntArray();
        float[] logits = new float[NUM_CLASSES];
        for (int i = 0; i < NUM_CLASSES; i++) {
            logits[i] = (quantizedOutput[i] - outputZeroPoint) * outputScale;
        }

        // 처음 5개 로직
        for (int i = 0; i < 5; i++) {
            Log.d(TAG, "🔢 Raw Output[" + i + "]: " + quantizedOutput[i] + " -> Logit: " + logits[i]);
        }

        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) maxLogit = logit;
        }
        Log.d("ImageClassifier", "📈 Max Logit Before Softmax: " + maxLogit);

        float sumExp = 0;
        float[] probabilities = new float[NUM_CLASSES];
        for (int i = 0; i < NUM_CLASSES; i++) {
            probabilities[i] = (float) Math.exp(logits[i] - maxLogit);
            sumExp += probabilities[i];
        }
        for (int i = 0; i < NUM_CLASSES; i++) {
            probabilities[i] = (probabilities[i] / sumExp) * 100;
        }

        // 결과 처리
        List<Pair<String, Float>> results = new ArrayList<>();
        for (int i = 0; i < NUM_CLASSES && i < labels.size(); i++) {
            results.add(new Pair<>(labels.get(i), probabilities[i]));
        }

        // 확률 기준 내림차순 정렬
        Collections.sort(results, (o1, o2) -> Float.compare(o2.second, o1.second));

        // 상위 5개 결과 로깅
        int topK = Math.min(5, results.size());
        for (int i = 0; i < topK; i++) {
            Log.d(TAG, "🔥 Softmax Top [" + i + "]: " + results.get(i).first + " - " + results.get(i).second + "%");
        }

        return results.subList(0, topK);
    }

    public synchronized void close() {
        try {
            if (tflite != null) {
                tflite.close();
                Log.d(TAG, "TFLite 인터프리터 닫기 완료");
            }
        } catch (Exception e) {
            Log.e(TAG, "리소스 해제 중 오류: " + e.getMessage(), e);
        }
    }
}