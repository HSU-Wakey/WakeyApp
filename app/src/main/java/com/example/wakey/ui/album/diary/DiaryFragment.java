package com.example.wakey.ui.album.diary;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class DiaryFragment extends Fragment {

    private static final int REQUEST_CREATE_DIARY = 1001;

    private RecyclerView diaryRecyclerView;
    private LinearLayout emptyDiaryState;
    private FloatingActionButton addDiaryFab;

    private DiaryAdapter diaryAdapter;
    private DiaryRepository diaryRepository;
    private List<DiaryItem> diaryItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_diary, container, false);

        diaryRepository = DiaryRepository.getInstance(requireContext());

        diaryRecyclerView = view.findViewById(R.id.diaryRecyclerView);
        emptyDiaryState = view.findViewById(R.id.emptyDiaryState);
        addDiaryFab = view.findViewById(R.id.addDiaryFab);

        // Setup RecyclerView
        diaryRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        diaryAdapter = new DiaryAdapter(diaryItems, item -> {
            // Open diary detail activity in view mode
            Intent intent = new Intent(getActivity(), DiaryDetailActivity.class);
            intent.putExtra("DIARY_TITLE", item.getTitle());
            intent.putExtra("DIARY_DATE_RANGE", item.getDateRange());
            intent.putExtra("DIARY_CONTENT", diaryRepository.getDiaryContent(item.getTitle()));
            intent.putExtra("DIARY_RATING", item.getHeartCount());
            intent.putExtra("DIARY_THUMBNAIL", item.getThumbnailPath());
            startActivity(intent);
        });

        diaryRecyclerView.setAdapter(diaryAdapter);

        // Setup FAB for creating new diary
        addDiaryFab.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), DiaryDetailActivity.class);
            startActivityForResult(intent, REQUEST_CREATE_DIARY);
        });

        loadDiaryEntries();

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CREATE_DIARY && resultCode == getActivity().RESULT_OK && data != null) {
            // Create a new diary item from the returned data
            String title = data.getStringExtra("DIARY_TITLE");
            String dateRange = data.getStringExtra("DIARY_DATE_RANGE");
            String thumbnailPath = data.getStringExtra("DIARY_THUMBNAIL");
            String content = data.getStringExtra("DIARY_CONTENT");
            int heartCount = data.getIntExtra("DIARY_RATING", 0);

            // 사용자가 이미지를 선택하지 않았다면 기본 이미지 사용
            if (thumbnailPath == null || thumbnailPath.isEmpty()) {
                thumbnailPath = "android.resource://" + getActivity().getPackageName() + "/" + R.drawable.placeholder_image;
            }

            DiaryItem newDiary = new DiaryItem(title, dateRange, thumbnailPath, heartCount);

            // DiaryRepository에 추가
            diaryRepository.addDiary(newDiary);

            // UI 업데이트
            loadDiaryEntries();
        }
    }

    private void loadDiaryEntries() {
        diaryItems.clear();
        diaryItems.addAll(diaryRepository.getDiaries());
        diaryAdapter.notifyDataSetChanged();

        // 빈 화면 상태 업데이트
        if (diaryItems.isEmpty()) {
            emptyDiaryState.setVisibility(View.VISIBLE);
            diaryRecyclerView.setVisibility(View.GONE);
        } else {
            emptyDiaryState.setVisibility(View.GONE);
            diaryRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 다이어리 내용이 업데이트되었을 수 있으므로 다시 로드
        loadDiaryEntries();
    }
}