package com.example.wakey;

import android.app.Application;

import com.example.wakey.manager.ApiManager;
import com.example.wakey.manager.DataManager;
import com.example.wakey.manager.MapManager;
import com.example.wakey.manager.UIManager;
import com.example.wakey.util.ToastManager;

/**
 * 애플리케이션 클래스 - 앱 전역 초기화 담당 (앱전초담)
 */
public class WakeyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 매니저 초기화 (매초)
        initializeManagers();
    }

    /**
     * 전역 매니저 초기화 (전매초)
     */
    private void initializeManagers() {
        // Toast 매니저 초기화
        ToastManager.getInstance().setContext(this);

        // 각 매니저 인스턴스 초기화 (컨텍스트만 설정, 세부 초기화는 MainActivity에서 수행)
        MapManager.getInstance(this);
        UIManager.getInstance(this);
        DataManager.getInstance(this);
        ApiManager.getInstance(this);
    }
}