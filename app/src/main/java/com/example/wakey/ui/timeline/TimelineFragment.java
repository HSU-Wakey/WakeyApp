package com.example.wakey.ui.timeline;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.model.TimelineItem;

import java.util.List;

public class TimelineFragment extends Fragment {

    private RecyclerView recyclerView;
    private TimelineAdapter adapter;
    private List<TimelineItem> timelineItems;

    public TimelineFragment() {}

    // ✅ 외부(MainActivity, UIManager 등)에서 데이터 주입
    public void setTimelineItems(List<TimelineItem> items) {
        Log.d("WakeyFlow", "🪄 TimelineFragment.setTimelineItems() 호출됨 → " + (items != null ? items.size() : 0) + "개");
        this.timelineItems = items;
        if (adapter != null) {
            adapter.updateItems(items);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.bottom_sheet_timeline, container, false);

        recyclerView = view.findViewById(R.id.timelineRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 초기화 시 빈 리스트 또는 전달된 리스트로 어댑터 설정
        adapter = new TimelineAdapter(timelineItems != null ? timelineItems : List.of());
        recyclerView.setAdapter(adapter);

        return view;
    }
}
