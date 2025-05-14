package com.example.wakey.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

public class ClipImageEncoder
{
    private static final String MODEL_PATH = "openai_clip-clipimageencoder-snapdragon_8_elite.tflite";
    private static final int IMAGE_SIZE = 224;
    private static final int EMBEDDING_DIM = 512;

    private Interpreter interpreter;

    public ClipImageEncoder(Context context) throws IOException
    {
        if (interpreter == null)
        {
            MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context, "openai_clip-clipimageencoder-snapdragon_8_elite.tflite");
            interpreter = new Interpreter(modelBuffer);
        }
    }

    public float[] getImageEncoding(Bitmap bitmap)
    {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);

        ByteBuffer input = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3 * 4).order(ByteOrder.nativeOrder());

        for (int y = 0; y < IMAGE_SIZE; y++)
        {
            for (int x = 0; x < IMAGE_SIZE; x++)
            {
                int pixel = resized.getPixel(x, y);
                input.putFloat(Color.red(pixel) / 255.0f);
                input.putFloat(Color.green(pixel) / 255.0f);
                input.putFloat(Color.blue(pixel) / 255.0f);
            }
        }

        float[][] output = new float[1][EMBEDDING_DIM];
        interpreter.run(input, output);
        return output[0];
    }

    public void close()
    {
        if (interpreter != null)
        {
            interpreter.close();
            interpreter = null;
        }
    }
}
