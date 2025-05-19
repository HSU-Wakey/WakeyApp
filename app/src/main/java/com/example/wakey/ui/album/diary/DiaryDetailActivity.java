package com.example.wakey.ui.album.diary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.wakey.R;
import com.example.wakey.audio.AudioRecorder;
import com.example.wakey.audio.MelSpectrogramGenerator;
import com.example.wakey.audio.WhisperDecoder;
import com.example.wakey.audio.WhisperEncoder;
import com.example.wakey.audio.WhisperTokenizer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.Map;

public class DiaryDetailActivity extends AppCompatActivity {
    private static final String TAG = "녹음디버그";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int TOKEN_SOT = 50257;
    private static final int TOKEN_EOT = 50256;
    private static final int TOKEN_NO_TIMESTAMP = 50362;
    private static final int TOKEN_TIMESTAMP_BEGIN = 50363;
    private static final int TOKEN_NO_SPEECH = 50361;
    private static final float NO_SPEECH_THR = 0.6f;
    private static final int[] NON_SPEECH_TOKENS = {
            1, 2, 7, 8, 9, 10, 14, 25, 26, 27, 28, 29, 31, 58, 59, 60, 61, 62, 63, 90, 91, 92, 93,
            357, 366, 438, 532, 685, 705, 796, 930, 1058, 1220, 1267, 1279, 1303, 1343, 1377, 1391,
            1635, 1782, 1875, 2162, 2361, 2488, 3467, 4008, 4211, 4600, 4808, 5299, 5855, 6329,
            7203, 9609, 9959, 10563, 10786, 11420, 11709, 11907, 13163, 13697, 13700, 14808, 15306,
            16410, 16791, 17992, 19203, 19510, 20724, 22305, 22935, 27007, 30109, 30420, 33409,
            34949, 40283, 40493, 40549, 47282, 49146, 50257, 50357, 50358, 50359, 50360, 50361
    };

    private AudioRecorder audioRecorder;
    private MelSpectrogramGenerator melSpectrogramGenerator;
    private WhisperEncoder whisperEncoder;
    private WhisperDecoder whisperDecoder;
    private WhisperTokenizer whisperTokenizer;

    private FloatingActionButton recordButton;
    private EditText diaryContent;
    private TextView recordingStatusText;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_detail);

        recordButton = findViewById(R.id.recordVoiceFab);
        diaryContent = findViewById(R.id.diaryContentEdit);
        recordingStatusText = findViewById(R.id.recordingStatusText);

        // 권한 확인 및 초기화 연기
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            initializeAudioComponents();
        }

        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        });
    }

    private void initializeAudioComponents() {
        try {
            audioRecorder = new AudioRecorder();
            melSpectrogramGenerator = new MelSpectrogramGenerator(this);
            whisperEncoder = new WhisperEncoder(this);
            whisperDecoder = new WhisperDecoder(this);
            whisperTokenizer = new WhisperTokenizer(this);
            Log.d(TAG, "오디오 컴포넌트 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "오디오 컴포넌트 초기화 실패: " + e.getMessage(), e);
            Toast.makeText(this, "오디오 초기화 실패", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAudioComponents();
            } else {
                Toast.makeText(this, "녹음 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                recordButton.setEnabled(false); // 권한 없으면 버튼 비활성화
            }
        }
    }

    private void startRecording() {
        if (audioRecorder == null) {
            Toast.makeText(this, "녹음 기능을 사용할 수 없습니다. 권한을 확인하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            audioRecorder.startRecording();
            isRecording = true;
            recordButton.setImageResource(R.drawable.ic_baseline_stop_24);
            recordingStatusText.setText("Recording...");
            Log.d(TAG, "녹음 시작");
        } catch (Exception e) {
            Log.e(TAG, "녹음 시작 실패: " + e.getMessage(), e);
            Toast.makeText(this, "녹음 시작 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (audioRecorder == null) {
            return;
        }
        try {
            float[] audioData = audioRecorder.stopRecordingAndGetData();
            isRecording = false;
            recordButton.setImageResource(R.drawable.ic_baseline_mic_24);
            recordingStatusText.setText("");
            Log.d(TAG, "녹음 중지, Audio data length: " + audioData.length);

            String transcription = transcribeAudio(audioData);
            diaryContent.setText(transcription);
            Log.d(TAG, "Transcription: " + transcription);
        } catch (Exception e) {
            Log.e(TAG, "녹음 중지 또는 처리 실패: " + e.getMessage(), e);
            Toast.makeText(this, "녹음 처리 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private String transcribeAudio(float[] audioData) {
        try {
            if (melSpectrogramGenerator == null) {
                Log.e(TAG, "MelSpectrogramGenerator가 초기화되지 않음");
                Toast.makeText(this, "Mel 필터 초기화 실패", Toast.LENGTH_SHORT).show();
                return "Mel 필터 초기화 실패";
            }
            float[][][] melSpectrogram = melSpectrogramGenerator.generateMelSpectrogram(audioData);
            if (melSpectrogram.length == 0) {
                Log.e(TAG, "Mel 스펙트로그램 생성 실패");
                Toast.makeText(this, "Mel 스펙트로그램 생성 실패", Toast.LENGTH_SHORT).show();
                return "Mel 스펙트로그램 생성 실패";
            }
            Log.d(TAG, "Mel shape: [" + melSpectrogram.length + ", " + melSpectrogram[0].length + ", " + melSpectrogram[0][0].length + "]");

            Map<String, Object> encoderOutput = whisperEncoder.runInference(melSpectrogram);
            float[][][][][] kCacheCross = (float[][][][][]) encoderOutput.get("kCacheCross");
            float[][][][][] vCacheCross = (float[][][][][]) encoderOutput.get("vCacheCross");
            float[][][] embedding = (float[][][]) encoderOutput.get("embedding");

            whisperDecoder.initializeCache(kCacheCross, vCacheCross);
            whisperDecoder.resetCache();

            int[] tokens = new int[]{TOKEN_SOT};
            StringBuilder transcription = new StringBuilder();
            float sumLogprobs = 0f;

            for (int i = 0; i < 224; i++) {
                Map<String, Object> decoderOutput = whisperDecoder.run(embedding, tokens[tokens.length - 1], i);
                float[][][][] logits = (float[][][][]) decoderOutput.get("logits");
                float[] lastLogits = logits[0][0][0];

                float[] logprobs = applyTimestampRules(lastLogits, tokens, i);
                if (i == 0) {
                    float noSpeechProb = (float) Math.exp(logprobs[TOKEN_NO_SPEECH]);
                    if (noSpeechProb > NO_SPEECH_THR) {
                        Log.d(TAG, "무음 감지, no_speech_prob: " + noSpeechProb);
                        return "";
                    }
                    lastLogits[TOKEN_EOT] = Float.NEGATIVE_INFINITY;
                    lastLogits[220] = Float.NEGATIVE_INFINITY;
                }
                for (int token : NON_SPEECH_TOKENS) {
                    lastLogits[token] = Float.NEGATIVE_INFINITY;
                }

                int nextToken = argmax(lastLogits);
                sumLogprobs += logprobs[nextToken];

                if (nextToken == TOKEN_EOT) {
                    break;
                }

                tokens = Arrays.copyOf(tokens, tokens.length + 1);
                tokens[tokens.length - 1] = nextToken;

                String text = whisperTokenizer.decode(new int[]{nextToken});
                transcription.append(text);
            }

            String result = transcription.toString().trim();
            Log.d(TAG, "Transcription completed, sum_logprobs: " + sumLogprobs);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "음성 변환 실패: " + e.getMessage(), e);
            Toast.makeText(this, "음성 변환 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return "음성 변환 실패";
        }
    }

    private float[] applyTimestampRules(float[] logits, int[] tokens, int index) {
        float[] logprobs = softmax(logits);
        float[] logitsCopy = Arrays.copyOf(logits, logits.length);

        // 타임스탬프 강제
        logitsCopy[TOKEN_NO_TIMESTAMP] = Float.NEGATIVE_INFINITY;

        // 타임스탬프 쌍 규칙
        int[] seq = Arrays.copyOfRange(tokens, 1, tokens.length);
        boolean lastWasTimestamp = seq.length >= 1 && seq[seq.length - 1] >= TOKEN_TIMESTAMP_BEGIN;
        boolean penultimateWasTimestamp = seq.length >= 2 && seq[seq.length - 2] >= TOKEN_TIMESTAMP_BEGIN;
        if (lastWasTimestamp) {
            if (penultimateWasTimestamp) {
                for (int j = TOKEN_TIMESTAMP_BEGIN; j < logitsCopy.length; j++) {
                    logitsCopy[j] = Float.NEGATIVE_INFINITY;
                }
            } else {
                for (int j = 0; j < TOKEN_EOT; j++) {
                    logitsCopy[j] = Float.NEGATIVE_INFINITY;
                }
            }
        }

        // 타임스탬프 감소 방지
        int[] timestamps = Arrays.stream(tokens)
                .filter(t -> t >= TOKEN_TIMESTAMP_BEGIN)
                .toArray();
        if (timestamps.length > 0) {
            int lastTimestamp = lastWasTimestamp && !penultimateWasTimestamp ?
                    timestamps[timestamps.length - 1] :
                    timestamps[timestamps.length - 1] + 1;
            for (int j = TOKEN_TIMESTAMP_BEGIN; j < lastTimestamp; j++) {
                logitsCopy[j] = Float.NEGATIVE_INFINITY;
            }
        }

        if (index == 0) {
            for (int j = 0; j < TOKEN_TIMESTAMP_BEGIN; j++) {
                logitsCopy[j] = Float.NEGATIVE_INFINITY;
            }
            int lastAllowed = TOKEN_TIMESTAMP_BEGIN + 50; // max_initial_timestamp_index = 1.0 / 0.02
            for (int j = lastAllowed + 1; j < logitsCopy.length; j++) {
                logitsCopy[j] = Float.NEGATIVE_INFINITY;
            }
        }

        // 타임스탬프 우선
        float timestampLogprob = logSumExp(logprobs, TOKEN_TIMESTAMP_BEGIN, logprobs.length);
        float maxTextTokenLogprob = max(logprobs, 0, TOKEN_TIMESTAMP_BEGIN);
        if (timestampLogprob > maxTextTokenLogprob) {
            for (int j = 0; j < TOKEN_TIMESTAMP_BEGIN; j++) {
                logitsCopy[j] = Float.NEGATIVE_INFINITY;
            }
        }

        return logprobs;
    }

    private float[] softmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            maxLogit = Math.max(maxLogit, logit);
        }
        float sumExp = 0f;
        float[] expLogits = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            expLogits[i] = (float) Math.exp(logits[i] - maxLogit);
            sumExp += expLogits[i];
        }
        float[] logprobs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            logprobs[i] = (float) Math.log(expLogits[i] / sumExp);
        }
        return logprobs;
    }

    private float logSumExp(float[] array, int start, int end) {
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            maxVal = Math.max(maxVal, array[i]);
        }
        float sum = 0f;
        for (int i = start; i < end; i++) {
            sum += (float) Math.exp(array[i] - maxVal);
        }
        return maxVal + (float) Math.log(sum);
    }

    private float max(float[] array, int start, int end) {
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            maxVal = Math.max(maxVal, array[i]);
        }
        return maxVal;
    }

    private int argmax(float[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (whisperEncoder != null) whisperEncoder.close();
        if (whisperDecoder != null) whisperDecoder.close();
        if (audioRecorder != null) audioRecorder.release();
    }
}