package com.example.wakey.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Toast 메시지 관리를 위한 유틸리티 클래스
 * - Android는 앱당 최대 5개의 Toast만 허용하므로 관리 필요
 */
public class ToastManager {
    private static ToastManager instance;
    private final Queue<String> toastQueue = new LinkedList<>();
    private boolean isShowing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context context;

    private ToastManager() {
        // 싱글톤 패턴
    }

    /**
     * ToastManager 인스턴스 가져오기
     */
    public static synchronized ToastManager getInstance() {
        if (instance == null) {
            instance = new ToastManager();
        }
        return instance;
    }

    /**
     * 컨텍스트 설정
     */
    public void setContext(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Toast 메시지 표시
     *
     * @param message 표시할 메시지
     * @param duration Toast 지속 시간 (Toast.LENGTH_SHORT 또는 Toast.LENGTH_LONG)
     */
    public void showToast(String message, int duration) {
        if (context == null) {
            return;
        }

        // 큐에 메시지 추가
        toastQueue.offer(message);

        // 토스트가 표시 중이 아니면 표시 시작
        if (!isShowing) {
            showNextToast(duration);
        }
    }

    /**
     * 간편 토스트 메시지 (짧은 지속시간)
     */
    public void showToast(String message) {
        showToast(message, Toast.LENGTH_SHORT);
    }

    /**
     * 다음 Toast 메시지 표시
     */
    private void showNextToast(int duration) {
        if (toastQueue.isEmpty()) {
            isShowing = false;
            return;
        }

        isShowing = true;
        String message = toastQueue.poll();

        handler.post(() -> {
            Toast toast = Toast.makeText(context, message, duration);
            toast.show();

            // 토스트 지속시간 후에 다음 토스트 표시
            int delayMillis = (duration == Toast.LENGTH_SHORT) ? 2000 : 3500;
            handler.postDelayed(() -> showNextToast(duration), delayMillis);
        });
    }
}