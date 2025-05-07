package com.example.wakey.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.flex.FlexDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class Upscaler {
    private final Interpreter interpreter;
    private final int inputWidth;
    private final int inputHeight;

    public Upscaler(Context context) throws IOException {
        // 1) 모델 파일 로드 (assets/real_esrgan_general_x4v3-qualcomm_snapdragon_8_elite.tflite)
        MappedByteBuffer modelBuffer = loadModelFile(
                context,
                "real_esrgan_general_x4v3-qualcomm_snapdragon_8_elite.tflite"
        );

        // 2) Flex Delegate 추가하여 InterpreterOptions 구성
        Interpreter.Options options = new Interpreter.Options()
                .addDelegate(new FlexDelegate());

        // 3) Interpreter 생성
        interpreter = new Interpreter(modelBuffer, options);

        // 4) 입력 텐서 크기 동적으로 읽기
        int[] inShape = interpreter.getInputTensor(0).shape();  // [1, H, W, 3]
        inputHeight = inShape[1];
        inputWidth  = inShape[2];
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFileName) throws IOException {
        FileInputStream inputStream = context.getAssets()
                .openFd(modelFileName)
                .createInputStream();
        FileChannel fc = inputStream.getChannel();
        long start = context.getAssets().openFd(modelFileName).getStartOffset();
        long len   = context.getAssets().openFd(modelFileName).getDeclaredLength();
        return fc.map(FileChannel.MapMode.READ_ONLY, start, len);
    }

    public Bitmap upscale(Bitmap input) {
        // 1) 입력 이미지를 모델 입력 사이즈에 맞게 리사이즈
        Bitmap resized = Bitmap.createScaledBitmap(input, inputWidth, inputHeight, true);

        // 2) 입력 버퍼 만들기
        ByteBuffer inBuf = ByteBuffer.allocateDirect(1 * inputHeight * inputWidth * 3 * 4)
                .order(ByteOrder.nativeOrder());
        for (int y = 0; y < inputHeight; y++) {
            for (int x = 0; x < inputWidth; x++) {
                int p = resized.getPixel(x, y);
                inBuf.putFloat(Color.red(p) / 255.0f);
                inBuf.putFloat(Color.green(p) / 255.0f);
                inBuf.putFloat(Color.blue(p) / 255.0f);
            }
        }
        inBuf.rewind();

        // 3) 출력 텐서 준비
        int[] outShape = interpreter.getOutputTensor(0).shape();  // [1, OH, OW, 3]
        int OB = outShape[0], OH = outShape[1], OW = outShape[2], OC = outShape[3];
        float[][][][] outArr = new float[OB][OH][OW][OC];

        // 4) 모델 실행
        interpreter.run(inBuf, outArr);

        // 5) 결과를 Bitmap 으로 변환
        Bitmap outBmp = Bitmap.createBitmap(OW, OH, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < OH; y++) {
            for (int x = 0; x < OW; x++) {
                int r = Math.min(255, Math.max(0, (int)(outArr[0][y][x][0] * 255)));
                int g = Math.min(255, Math.max(0, (int)(outArr[0][y][x][1] * 255)));
                int b = Math.min(255, Math.max(0, (int)(outArr[0][y][x][2] * 255)));
                outBmp.setPixel(x, y, Color.rgb(r, g, b));
            }
        }
        return outBmp;
    }

    public void close() {
        interpreter.close();
    }
}
