package com.example.wakey.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Yolov8Detector {
    private static final String TAG = "Yolov8Detector";
    private static final String MODEL_PATH = "yolov8_det.tflite";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.4f;
    private static final float IOU_THRESHOLD = 0.5f;
    private static final int NUM_CLASSES = 80;

    private final Interpreter tflite;

    public Yolov8Detector(Context context) throws IOException {
        MappedByteBuffer model = FileUtil.loadMappedFile(context, MODEL_PATH);
        tflite = new Interpreter(model, new Interpreter.Options());
        Log.d(TAG, "✅ YOLOv8 모델 로드 완료");
    }

    public List<RectF> detect(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = resized.getPixel(x, y);
                inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
                inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
                inputBuffer.putFloat((pixel & 0xFF) / 255.0f);
            }
        }

        float[][][] boxesOutput = new float[1][4][8400];
        float[][][] scoresOutput = new float[1][NUM_CLASSES][8400];

        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, boxesOutput);
        outputMap.put(1, scoresOutput);

        tflite.runForMultipleInputsOutputs(new Object[]{inputBuffer}, outputMap);

        List<RectF> result = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();

        for (int i = 0; i < 8400; i++) {
            float x = boxesOutput[0][0][i];
            float y = boxesOutput[0][1][i];
            float w = boxesOutput[0][2][i];
            float h = boxesOutput[0][3][i];

            float maxScore = -1f;
            int classId = -1;
            for (int c = 0; c < NUM_CLASSES; c++) {
                float score = scoresOutput[0][c][i];
                if (score > maxScore) {
                    maxScore = score;
                    classId = c;
                }
            }

            if (maxScore < CONFIDENCE_THRESHOLD) continue;

            float left = (x - w / 2f) * bitmap.getWidth() / INPUT_SIZE;
            float top = (y - h / 2f) * bitmap.getHeight() / INPUT_SIZE;
            float right = (x + w / 2f) * bitmap.getWidth() / INPUT_SIZE;
            float bottom = (y + h / 2f) * bitmap.getHeight() / INPUT_SIZE;

            RectF rect = new RectF(left, top, right, bottom);
            result.add(rect);
            confidences.add(maxScore);

            Log.d(TAG, String.format("📦 [%.2f] Class %d: %s", maxScore, classId, rect));
        }

        List<RectF> finalBoxes = applyNMS(result, confidences, IOU_THRESHOLD);
        Log.d(TAG, "📦 최종 박스 수: " + finalBoxes.size());
        return finalBoxes;
    }

    private List<RectF> applyNMS(List<RectF> boxes, List<Float> scores, float iouThreshold) {
        List<RectF> finalBoxes = new ArrayList<>();

        while (!boxes.isEmpty()) {
            int maxIdx = 0;
            for (int i = 1; i < scores.size(); i++) {
                if (scores.get(i) > scores.get(maxIdx)) maxIdx = i;
            }

            RectF selected = boxes.remove(maxIdx);
            float selectedScore = scores.remove(maxIdx);
            finalBoxes.add(selected);

            for (int i = boxes.size() - 1; i >= 0; i--) {
                if (calculateIoU(selected, boxes.get(i)) > iouThreshold) {
                    boxes.remove(i);
                    scores.remove(i);
                }
            }
        }

        return finalBoxes;
    }

    private float calculateIoU(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);

        float intersection = Math.max(right - left, 0) * Math.max(bottom - top, 0);
        float union = a.width() * a.height() + b.width() * b.height() - intersection;

        return intersection / union;
    }

    public void close() {
        tflite.close();
    }
}
