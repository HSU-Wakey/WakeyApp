package com.example.wakey.tflite;

import android.content.Context;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

public class ClipTextEncoder {
    private static final String MODEL_PATH = "openai_clip-cliptextencoder.tflite";
    private static final int MAX_TOKENS = 77;
    private static final int EMBEDDING_DIM = 512;

    private final Interpreter interpreter;

    public ClipTextEncoder(Context context) throws IOException {
        MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH);
        interpreter = new Interpreter(modelBuffer);
    }

    public float[] getTextEncoding(int[] tokenIds) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(MAX_TOKENS * 4).order(ByteOrder.nativeOrder());

        for (int i = 0; i < MAX_TOKENS; i++) {
            inputBuffer.putInt(i < tokenIds.length ? tokenIds[i] : 0);  // padding
        }

        float[][] output = new float[1][EMBEDDING_DIM];
        interpreter.run(inputBuffer, output);
        return output[0];
    }

    public void close() {
        interpreter.close();
    }
}
