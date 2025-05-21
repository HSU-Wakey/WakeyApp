package com.example.wakey.ui.timeline;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.Context;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.local.PhotoDao;
import com.example.wakey.data.model.TimelineItem;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoryFragment extends Fragment {

    private static final String TAG = "StoryFragment";
    private RecyclerView recyclerView;
    private StoryAdapter storyAdapter;
    private String currentDate;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean pendingLoad = false;
    private Context applicationContext;
    private StoryGenerator storyGenerator;

    public StoryFragment() {
        // 필수 빈 생성자
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 애플리케이션 컨텍스트 저장 (항상 유효함)
        applicationContext = context.getApplicationContext();

        // StoryGenerator 초기화 및 가져오기
        storyGenerator = StoryGenerator.getInstance(applicationContext);

        // 대기 중인 로드가 있으면 실행
        if (pendingLoad && currentDate != null) {
            Log.d(TAG, "⭐ onAttach - 대기 중이던 로드 실행: " + currentDate);
            loadStoriesForDate(currentDate);
            pendingLoad = false;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // 바텀 시트 레이아웃을 재활용
        View view = inflater.inflate(R.layout.bottom_sheet_timeline, container, false);

        // RecyclerView 초기화
        recyclerView = view.findViewById(R.id.storyRecyclerView);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

            // 빈 어댑터 설정
            List<TimelineItem> emptyList = new ArrayList<>();
            storyAdapter = new StoryAdapter(emptyList);
            recyclerView.setAdapter(storyAdapter);

            // 가시성 명시적 설정
            recyclerView.setVisibility(View.VISIBLE);

            // 다른 RecyclerView 숨기기
            RecyclerView timelineRecyclerView = view.findViewById(R.id.timelineRecyclerView);
            if (timelineRecyclerView != null) {
                timelineRecyclerView.setVisibility(View.GONE);
            }

            // 클릭 리스너 설정
            storyAdapter.setOnItemClickListener((item, position) -> {
                Log.d(TAG, "스토리 항목 클릭: " + item.getPhotoPath());
            });

            // StoryGenerator에 어댑터 설정
            storyGenerator.setStoryAdapter(storyAdapter);

            // 매니저에 어댑터 등록 (TimelineManager와 StoryAdapter 연결)
            TimelineManager.getInstance(requireContext()).setStoryAdapter(storyAdapter);
        } else {
            Log.e(TAG, "storyRecyclerView를 찾을 수 없음");
        }

        // 현재 날짜로 초기 데이터 로드
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        loadStoriesForDate(sdf.format(new Date()));

        return view;
    }

    // 특정 날짜의 스토리 로드
    public void loadStoriesForDate(String dateString) {
        Log.d(TAG, "날짜 스토리 로드: " + dateString);
        this.currentDate = dateString;

        // 프래그먼트가 연결되어 있지 않으면 대기 상태로 표시
        if (!isAdded()) {
            Log.d(TAG, "프래그먼트가 아직 연결되지 않음. 나중에 로드할 예정: " + dateString);
            pendingLoad = true;
            return;
        }

        // 백그라운드에서 스토리 로드
        executor.execute(() -> {
            try {
                List<TimelineItem> stories = storyGenerator.getStoriesForDate(parseDate(dateString));

                // UI 스레드에서 어댑터 업데이트
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded()) {
                        if (stories != null && !stories.isEmpty()) {
                            Log.d(TAG, "스토리 로드 완료 - 항목 수: " + stories.size());
                            for (TimelineItem item : stories) {
                                Log.d(TAG, "스토리 항목: " + item.getPhotoPath() + ", 스토리: " +
                                        (item.getStory() != null ? item.getStory() : "없음"));
                            }
                            storyAdapter.setItems(stories);
                            storyAdapter.notifyDataSetChanged();
                        } else {
                            Log.d(TAG, "로드된 스토리 없음");
                            storyAdapter.setItems(new ArrayList<>());
                            storyAdapter.notifyDataSetChanged();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "스토리 로드 중 오류: " + e.getMessage(), e);
            }
        });
    }

    // 날짜 문자열을 Date 객체로 변환
    private Date parseDate(String dateString) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return format.parse(dateString);
        } catch (Exception e) {
            Log.e(TAG, "날짜 파싱 오류: " + dateString, e);
            return new Date();
        }
    }

    // 스토리 새로고침
    public void refreshStories() {
        Log.d(TAG, "⭐ refreshStories 호출 - 현재 날짜: " + currentDate);
        if (currentDate != null) {
            loadStoriesForDate(currentDate);
        }
    }

    // 자원 해제
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}