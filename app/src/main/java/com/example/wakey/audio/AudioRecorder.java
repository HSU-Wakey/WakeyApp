package com.example.wakey.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AudioRecorder {
    private static final String TAG = "녹음디버그";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHUNK_SECONDS = 30;
    private static final int SAMPLES_PER_CHUNK = SAMPLE_RATE * CHUNK_SECONDS;

    private final int bufferSize;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private List<Float> floatBufferList = new ArrayList<>();
    private Thread recordingThread;

    @SuppressLint("MissingPermission")
    public AudioRecorder() {
        Log.d(TAG, "AudioRecorder 생성자 호출됨");

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "버퍼 크기 오류 발생: " + bufferSize);
            throw new IllegalStateException("버퍼 크기 오류: " + bufferSize);
        }

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 초기화 실패 (STATE_INITIALIZED 아님)");
            throw new IllegalStateException("AudioRecord 초기화 실패");
        }

        Log.d(TAG, "AudioRecorder 정상적으로 초기화됨, sample_rate=" + SAMPLE_RATE);
    }

    public void startRecording() {
        isRecording = true;
        floatBufferList.clear();
        audioRecord.startRecording();

        recordingThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                for (int i = 0; i < read; i++) {
                    floatBufferList.add(buffer[i] / 32768.0f);
                }
            }
        });
        recordingThread.start();
        Log.d(TAG, "녹음 시작");
    }

    public float[] stopRecordingAndGetData() {
        isRecording = false;
        audioRecord.stop();
        try {
            if (recordingThread != null) {
                recordingThread.join();
                Log.d(TAG, "녹음 스레드 종료 완료");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "녹음 스레드 종료 대기 실패: " + e.getMessage());
        }

        float[] result = new float[floatBufferList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = floatBufferList.get(i);
        }
        floatBufferList.clear();

        // 청크 처리
        List<float[]> chunks = chunkAudio(result);
        if (chunks.size() > 1) {
            Log.w(TAG, "오디오 길이가 30초 초과, 첫 번째 청크만 반환");
        }
        float[] chunk = chunks.isEmpty() ? new float[0] : chunks.get(0);
        Log.d(TAG, "오디오 데이터 반환, length=" + chunk.length);

        return chunk;
    }

    private List<float[]> chunkAudio(float[] audio) {
        List<float[]> chunks = new ArrayList<>();
        int numFullChunks = audio.length / SAMPLES_PER_CHUNK;
        int lastChunkStart = numFullChunks * SAMPLES_PER_CHUNK;

        for (int i = 0; i < numFullChunks; i++) {
            float[] chunk = new float[SAMPLES_PER_CHUNK];
            System.arraycopy(audio, i * SAMPLES_PER_CHUNK, chunk, 0, SAMPLES_PER_CHUNK);
            chunks.add(chunk);
        }

        if (lastChunkStart < audio.length) {
            float[] lastChunk = new float[audio.length - lastChunkStart];
            System.arraycopy(audio, lastChunkStart, lastChunk, 0, lastChunk.length);
            chunks.add(lastChunk);
        }

        return chunks;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void release() {
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
            Log.d(TAG, "AudioRecorder 리소스 해제 완료");
        }
    }
}