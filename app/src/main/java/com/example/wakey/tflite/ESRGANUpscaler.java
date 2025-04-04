package com.example.wakey.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ESRGANUpscaler {
    private final Interpreter interpreter;
    private static final String TAG = "ESRGAN_UPSCALER";

    public ESRGANUpscaler(Context context) throws IOException {
        ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, "real_esrgan_general_x4v3-qualcomm_snapdragon_8_elite.tflite");
        interpreter = new Interpreter(modelBuffer);
        Log.d(TAG, "✅ ESRGAN 모델 로드 완료");
    }

    public Bitmap upscale(Bitmap input) {
        Bitmap resized = Bitmap.createScaledBitmap(input, 128, 128, true); // 입력 사이즈 맞춤
        Log.d(TAG, "📏 입력 이미지 리사이즈 완료: " + resized.getWidth() + "x" + resized.getHeight());

        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resized);

        // 모델 출력은 4배 사이즈: 512x512 RGB (float32 예상)
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(1 * 512 * 512 * 3 * 4); // float32 = 4 bytes
        outputBuffer.order(ByteOrder.nativeOrder());

        Log.d(TAG, "🚀 업스케일 시작");
        interpreter.run(inputBuffer, outputBuffer);
        Log.d(TAG, "✅ 업스케일 완료");

        return convertByteBufferToBitmap(outputBuffer, 512, 512);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 * 128 * 128 * 3 * 4); // float32
        buffer.order(ByteOrder.nativeOrder());

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int pixel = bitmap.getPixel(x, y);
                buffer.putFloat((Color.red(pixel) / 255.0f));
                buffer.putFloat((Color.green(pixel) / 255.0f));
                buffer.putFloat((Color.blue(pixel) / 255.0f));
            }
        }
        buffer.rewind();
        return buffer;
    }

    private Bitmap convertByteBufferToBitmap(ByteBuffer buffer, int width, int height) {
        buffer.rewind();
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = buffer.getFloat();
                float g = buffer.getFloat();
                float b = buffer.getFloat();

                int red = Math.min(255, Math.max(0, (int) (r * 255)));
                int green = Math.min(255, Math.max(0, (int) (g * 255)));
                int blue = Math.min(255, Math.max(0, (int) (b * 255)));

                int color = Color.rgb(red, green, blue);
                output.setPixel(x, y, color);
            }
        }

        Log.d(TAG, "🖼️ 출력 이미지 생성 완료: " + width + "x" + height);
        return output;
    }
}