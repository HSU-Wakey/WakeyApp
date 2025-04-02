// tflite/ImageClassifier.java
package com.example.wakey.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageClassifier {
    private static final String TAG = "ImageClassifier";
    private static final String MODEL_PATH = "mobilenet_v3_large_quantized.tflite";
    private static final String LABELS_PATH = "labels.txt";
    private static final int IMAGE_SIZE = 224;
    private static final int NUM_CLASSES = 1000;

    private Interpreter tflite;
    private List<String> labels;

    public ImageClassifier(Context context) throws IOException {
        // 모델과 라벨 파일 로드
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, MODEL_PATH);
        tflite = new Interpreter(tfliteModel);
        labels = FileUtil.loadLabels(context, LABELS_PATH);
    }

    public List<String> classifyTopK(Bitmap bitmap, int topK) {
        // 입력 이미지 전처리
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[IMAGE_SIZE * IMAGE_SIZE];
        resizedBitmap.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);

        for (int i = 0; i < intValues.length; i++) {
            int pixel = intValues[i];
            inputBuffer.put((byte) ((pixel >> 16) & 0xFF)); // R
            inputBuffer.put((byte) ((pixel >> 8) & 0xFF));  // G
            inputBuffer.put((byte) (pixel & 0xFF));         // B
        }

        // 출력 버퍼 준비
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(
                new int[]{1, NUM_CLASSES},
                DataType.FLOAT32
        );

        // 모델 실행
        tflite.run(inputBuffer, outputBuffer.getBuffer());

        float[] probabilities = outputBuffer.getFloatArray();

        // Top-K 인덱스 정렬
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            indices.add(i);
        }

        indices.sort((i1, i2) -> Float.compare(probabilities[i2], probabilities[i1]));

        List<String> results = new ArrayList<>();
        for (int i = 0; i < topK; i++) {
            int index = indices.get(i);
            String label = labels.get(index);
            float confidence = probabilities[index] * 100;
            results.add(String.format("%s: %.2f%%", label, confidence));
        }

        return results;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
        }
    }
}
