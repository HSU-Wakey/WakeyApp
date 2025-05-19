package com.example.wakey.audio;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.TransformType;

public class MelSpectrogramGenerator {
    private static final String TAG = "녹음디버그 Mel";
    private static final int N_MELS = 80;
    private static final int N_FFT = 400;
    private static final int SAMPLE_RATE = 16000;
    private static final int EXPECTED_FRAMES = 3000; // Whisper 요구: 30초
    private static final int EXPECTED_SAMPLES = 30 * SAMPLE_RATE; // 480,000 샘플
    private float[][] melFilter;

    public MelSpectrogramGenerator(Context context) {
        try {
            melFilter = loadMelFilter(context);
            if (melFilter == null || melFilter.length != N_MELS || melFilter[0].length != (N_FFT / 2 + 1)) {
                throw new RuntimeException("Mel 필터 형상 불일치, 기대: [" + N_MELS + ", " + (N_FFT / 2 + 1) + "]");
            }
            Log.d(TAG, "Mel filter loaded, shape: [" + melFilter.length + ", " + melFilter[0].length + "]");
        } catch (Exception e) {
            Log.e(TAG, "MelSpectrogramGenerator 초기화 실패: " + e.getMessage(), e);
            melFilter = null;
        }
    }

    private float[][] loadMelFilter(Context context) {
        try {
            InputStream inputStream = context.getAssets().open("mel_filters.bin");
            int expectedSize = N_MELS * (N_FFT / 2 + 1) * 4; // float32: 4 bytes
            ByteBuffer buffer = ByteBuffer.allocate(expectedSize);
            int bytesRead = Channels.newChannel(inputStream).read(buffer);
            inputStream.close();

            if (bytesRead != expectedSize) {
                throw new RuntimeException("Mel 필터 파일 크기 불일치, 기대: " + expectedSize + ", 실제: " + bytesRead);
            }

            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.rewind();

            float[][] melFilter = new float[N_MELS][N_FFT / 2 + 1];
            for (int i = 0; i < N_MELS; i++) {
                for (int j = 0; j < N_FFT / 2 + 1; j++) {
                    melFilter[i][j] = buffer.getFloat();
                }
            }
            return melFilter;
        } catch (Exception e) {
            Log.e(TAG, "Mel 필터 로드 실패: " + e.getMessage(), e);
            return null;
        }
    }

    public float[][][] generateMelSpectrogram(float[] audioData) {
        if (melFilter == null) {
            Log.e(TAG, "Mel 필터가 로드되지 않음");
            return new float[0][0][0];
        }

        // 오디오 데이터 패딩
        float[] paddedAudio = new float[EXPECTED_SAMPLES];
        Arrays.fill(paddedAudio, 0f);
        int copyLength = Math.min(audioData.length, EXPECTED_SAMPLES);
        System.arraycopy(audioData, 0, paddedAudio, 0, copyLength);
        Log.d(TAG, "Padded audio length: " + paddedAudio.length);

        int hop_length = N_FFT / 2; // 200
        int n_frames = (paddedAudio.length - N_FFT) / hop_length + 1;
        float[][][] melSpectrogram = new float[1][N_MELS][EXPECTED_FRAMES];

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        double[] window = new double[N_FFT];
        for (int i = 0; i < N_FFT; i++) {
            window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (N_FFT - 1))); // Hann window
        }

        for (int frame = 0; frame < Math.min(n_frames, EXPECTED_FRAMES); frame++) {
            double[] frameData = new double[N_FFT];
            for (int i = 0; i < N_FFT; i++) {
                int idx = frame * hop_length + i;
                frameData[i] = (idx < paddedAudio.length) ? paddedAudio[idx] * window[i] : 0;
            }

            // FFT 수행: Complex[] 반환
            Complex[] complexSpectrum = fft.transform(frameData, TransformType.FORWARD);
            double[] power = new double[N_FFT / 2 + 1];
            for (int i = 0; i < N_FFT / 2 + 1; i++) {
                double real = complexSpectrum[i].getReal();
                double imag = (i == 0 || i == N_FFT / 2) ? 0 : complexSpectrum[i].getImaginary();
                power[i] = real * real + imag * imag;
            }

            for (int mel = 0; mel < N_MELS; mel++) {
                double sum = 0;
                for (int i = 0; i < N_FFT / 2 + 1; i++) {
                    sum += power[i] * melFilter[mel][i];
                }
                melSpectrogram[0][mel][frame] = (float) Math.log(Math.max(sum, 1e-10));
            }
        }

        Log.d(TAG, "Mel 스펙트로그램 생성, shape: [" + melSpectrogram.length + ", " + melSpectrogram[0].length + ", " + melSpectrogram[0][0].length + "]");
        return melSpectrogram;
    }
}