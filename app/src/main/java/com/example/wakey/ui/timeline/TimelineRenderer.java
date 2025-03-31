package com.example.wakey.ui.timeline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 타임라인 시각적 렌더링 처리 클래스 (선, 점, 아이콘 제거됨)
 */
public class TimelineRenderer extends RecyclerView.ItemDecoration {

    public TimelineRenderer(Context context) {
        // 초기화 필요 없음 – 아무것도 그리지 않음
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        // 아무것도 그리지 않음
    }
}
