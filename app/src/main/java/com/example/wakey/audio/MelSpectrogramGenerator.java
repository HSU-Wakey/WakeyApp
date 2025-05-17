package com.example.wakey.audio;

import android.util.Log;

public class MelSpectrogramGenerator {
    private static final int SAMPLE_RATE = 16000;
    private static final int N_FFT = 400;        // FFT window size
    private static final int HOP_LENGTH = 160;   // 10ms step (160 samples at 16kHz)
    private static final int N_MELS = 80;        // Whisper uses 80 mel bins
    private static final int N_FRAMES = 3000;    // Whisper expects 3000 frames

    // Hann window 생성
    private static float[] hannWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (size - 1)));
        }
        return window;
    }

    public static float[][][] generate(float[] audio) {
        float[] window = hannWindow(N_FFT);

        int totalFrames = Math.min(N_FRAMES, 1 + (audio.length - N_FFT) / HOP_LENGTH);
        float[][] melSpectrogram = new float[N_MELS][N_FRAMES];

        // 간단한 STFT + mel 변환 대체: energy 기반 dummy 생성
        for (int t = 0; t < totalFrames; t++) {
            int start = t * HOP_LENGTH;
            float sum = 0f;

            for (int i = 0; i < N_FFT; i++) {
                if (start + i < audio.length) {
                    float sample = audio[start + i] * window[i];
                    sum += sample * sample;
                }
            }

            float energy = (float) Math.log10(sum + 1e-6);  // log energy

            // dummy mel 채우기 – 실제 mel 필터 대신 값 복사
            for (int m = 0; m < N_MELS; m++) {
                melSpectrogram[m][t] = energy;
            }
        }

        // Whisper는 float[1][80][3000] 구조 요구
        float[][][] result = new float[1][N_MELS][N_FRAMES];
        for (int m = 0; m < N_MELS; m++) {
            for (int t = 0; t < N_FRAMES; t++) {
                result[0][m][t] = melSpectrogram[m][t];
            }
        }

        return result;
    }
}
