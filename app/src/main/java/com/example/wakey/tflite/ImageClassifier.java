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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageClassifier {
    private static final String MODEL_PATH = "mobilenet_v3_large_quantized-snapdragon_8_gen_3.tflite";
    private static final int IMAGE_SIZE = 224;
    private static final int NUM_CLASSES = 1000;
    private Interpreter tflite;
    private List<String> labels;

    public ImageClassifier(Context context) throws IOException {
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, MODEL_PATH);
        Interpreter.Options options = new Interpreter.Options();
        tflite = new Interpreter(tfliteModel, options);
        labels = FileUtil.loadLabels(context, "labels.txt");
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

        // 추가 로그: 처음 5개 로짓
        for (int i = 0; i < 5; i++) {
            Log.d("ImageClassifier", "🔢 Raw Output[" + i + "]: " + quantizedOutput[i] + " -> Logit: " + logits[i]);
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

        List<Pair<String, Float>> results = new ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            results.add(new Pair<>(labels.get(i), probabilities[i]));
        }
        Collections.sort(results, (o1, o2) -> Float.compare(o2.second, o1.second));

        for (int i = 0; i < Math.min(5, results.size()); i++) {
            Log.d("ImageClassifier", "🔥 Softmax Top [" + i + "]: " + results.get(i).first + " - " + results.get(i).second + "%");
        }

        return results.subList(0, Math.min(5, results.size()));
    }

    public void close() {
        tflite.close();
    }
}