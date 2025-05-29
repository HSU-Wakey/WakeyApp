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
import android.widget.Toast;

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
                // 1. DB에서 기존 스토리 로드
                List<TimelineItem> stories = storyGenerator.getStoriesForDate(parseDate(dateString));

                // 2. UI 업데이트
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded() && storyAdapter != null) {
                        if (stories != null && !stories.isEmpty()) {
                            Log.d(TAG, "스토리 로드 완료 - 항목 수: " + stories.size());

                            // 스토리가 없는 항목 확인
                            List<TimelineItem> itemsNeedingStories = new ArrayList<>();
                            for (TimelineItem item : stories) {
                                if (item.getStory() == null || item.getStory().isEmpty()) {
                                    itemsNeedingStories.add(item);
                                    Log.d(TAG, "스토리 필요: " + item.getPhotoPath());
                                }
                            }

                            // 먼저 UI 업데이트 (기존 스토리 표시)
                            storyAdapter.setItems(stories);

                            // 스토리가 필요한 항목들에 대해 생성 요청
                            if (!itemsNeedingStories.isEmpty()) {
                                Log.d(TAG, "스토리 생성 필요 항목: " + itemsNeedingStories.size() + "개");
                                requestStoryGeneration(itemsNeedingStories);
                            }

                        } else {
                            Log.d(TAG, "로드된 스토리 없음");
                            storyAdapter.setItems(new ArrayList<>());
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "스토리 로드 중 오류: " + e.getMessage(), e);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "스토리 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // 스토리 생성 요청 메서드 추가
    private void requestStoryGeneration(List<TimelineItem> itemsNeedingStories) {
        if (storyGenerator != null) {
            storyGenerator.generateStories(itemsNeedingStories, new StoryGenerator.OnStoryGeneratedListener() {
                @Override
                public void onStoryGenerated(List<TimelineItem> itemsWithStories) {
                    Log.d(TAG, "스토리 생성 완료 - 콜백 받음");

                    // 생성된 스토리로 어댑터 업데이트
                    if (isAdded() && storyAdapter != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            // 개별 아이템 업데이트
                            for (TimelineItem item : itemsWithStories) {
                                storyAdapter.updateItem(item);
                            }
                        });
                    }
                }

                @Override
                public void onStoryGenerationFailed(Exception e) {
                    Log.e(TAG, "스토리 생성 실패: " + e.getMessage());
                }
            });
        }
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