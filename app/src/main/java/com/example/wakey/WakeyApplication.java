package com.example.wakey;

import android.app.Application;
import com.example.wakey.util.ToastManager;

public class WakeyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Toast 매니저 초기화
        ToastManager.getInstance().setContext(this);
    }
}