    package com.example.wakey.audio;

    import android.annotation.SuppressLint;
    import android.media.AudioFormat;
    import android.media.AudioRecord;
    import android.media.MediaRecorder;

    import java.util.ArrayList;
    import java.util.List;

    // AudioRecorder.java
    public class AudioRecorder {
        private static final int SAMPLE_RATE = 16000;
        private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

        private final int bufferSize;
        private final AudioRecord audioRecord;
        private boolean isRecording = false;
        private List<Float> floatBufferList = new ArrayList<>();
        private Thread recordingThread;

        @SuppressLint("MissingPermission")
        public AudioRecorder() {
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
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
        }

        public float[] stopRecordingAndGetData() {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();

            float[] result = new float[floatBufferList.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = floatBufferList.get(i);
            }
            return result;
        }

        public boolean isRecording() {
            return isRecording;
        }
    }
