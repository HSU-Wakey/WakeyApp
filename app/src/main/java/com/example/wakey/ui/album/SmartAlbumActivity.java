package com.example.wakey.ui.album;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.wakey.R;
import com.example.wakey.ui.album.domestic.DomesticFragment;
import com.example.wakey.ui.album.overseas.OverseasFragment;
import com.example.wakey.ui.album.diary.DiaryFragment;

public class SmartAlbumActivity extends AppCompatActivity {
    private static final String TAG = "SmartAlbumActivity";

    private TextView tabDomestic, tabWorld, tabRecord;
    private int currentTabIndex = 0; // 0: domestic, 1: world, 2: record

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_album);

        initViews();
        setupListeners();

        // 초기 탭 설정 (국내)
        updateTabSelection(0);
    }

    private void initViews() {
        tabDomestic = findViewById(R.id.tabDomestic);
        tabWorld = findViewById(R.id.tabWorld);
        tabRecord = findViewById(R.id.tabRecord);

        // Back button
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        tabDomestic.setOnClickListener(v -> {
            updateTabSelection(0);
        });

        tabWorld.setOnClickListener(v -> {
            updateTabSelection(1);
        });

        tabRecord.setOnClickListener(v -> {
            updateTabSelection(2);
        });
    }

    private void updateTabSelection(int tabIndex) {
        if (currentTabIndex == tabIndex) {
            return; // 이미 선택된 탭이면 변경하지 않음
        }

        currentTabIndex = tabIndex;

        // 모든 탭을 비선택 상태로 초기화
        tabDomestic.setBackgroundResource(R.drawable.tab_unselected_bg);
        tabDomestic.setTextColor(getResources().getColor(R.color.tab_unselected_text));

        tabWorld.setBackgroundResource(R.drawable.tab_unselected_bg);
        tabWorld.setTextColor(getResources().getColor(R.color.tab_unselected_text));

        tabRecord.setBackgroundResource(R.drawable.tab_unselected_bg);
        tabRecord.setTextColor(getResources().getColor(R.color.tab_unselected_text));

        // 선택된 탭 설정 및 해당 프래그먼트 로드
        Fragment selectedFragment = null;

        switch (tabIndex) {
            case 0: // Domestic
                tabDomestic.setBackgroundResource(R.drawable.tab_selected_bg);
                tabDomestic.setTextColor(getResources().getColor(R.color.white));
                selectedFragment = new DomesticFragment();
                break;

            case 1: // Overseas
                tabWorld.setBackgroundResource(R.drawable.tab_selected_bg);
                tabWorld.setTextColor(getResources().getColor(R.color.white));
                selectedFragment = new OverseasFragment();
                break;

            case 2: // Diary
                tabRecord.setBackgroundResource(R.drawable.tab_selected_bg);
                tabRecord.setTextColor(getResources().getColor(R.color.white));
                selectedFragment = new DiaryFragment();
                break;
        }

        // 선택된 프래그먼트로 교체
        if (selectedFragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragmentContainer, selectedFragment);
            transaction.commit();
        }
    }
}