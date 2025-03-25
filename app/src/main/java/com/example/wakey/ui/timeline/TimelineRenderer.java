// path: com.example.wakey/ui/timeline/TimelineRenderer.java

package com.example.wakey.ui.timeline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;

/**
 * 타임라인 시각적 렌더링 처리 클래스
 */
public class TimelineRenderer extends RecyclerView.ItemDecoration {

    private final Paint linePaint;
    private final Paint circlePaint;
    private final float lineWidth;
    private final float circleRadius;
    private final float circleGap;
    private final Drawable iconDrawable;

    public TimelineRenderer(Context context) {
        linePaint = new Paint();
        linePaint.setColor(ContextCompat.getColor(context, R.color.route_color)); // 타임라인 선 색상
        linePaint.setStrokeWidth(context.getResources().getDimension(R.dimen.timeline_line_width));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        circlePaint = new Paint();
        circlePaint.setColor(ContextCompat.getColor(context, R.color.route_color)); // 타임라인 포인트 색상
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);

        lineWidth = context.getResources().getDimension(R.dimen.timeline_line_width);
        circleRadius = context.getResources().getDimension(R.dimen.timeline_circle_radius);
        circleGap = context.getResources().getDimension(R.dimen.timeline_circle_gap);

        iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_timeline_point);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(c, parent, state);

        if (parent.getChildCount() == 0) return;

        // 타임라인 세로선 그리기
        float centerX = parent.getPaddingLeft() + circleRadius + circleGap;
        float top = parent.getPaddingTop();
        float bottom = parent.getHeight() - parent.getPaddingBottom();

        c.drawLine(centerX, top, centerX, bottom, linePaint);

        // 각 항목에 대한 타임라인 포인트 그리기
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            float centerY = child.getTop() + child.getHeight() / 2f;

            // 원 그리기
            c.drawCircle(centerX, centerY, circleRadius, circlePaint);

            // 아이콘 그리기 (있는 경우)
            if (iconDrawable != null) {
                int left = (int) (centerX - circleRadius);
                int top1 = (int) (centerY - circleRadius);
                int right = (int) (centerX + circleRadius);
                int bottom1 = (int) (centerY + circleRadius);

                iconDrawable.setBounds(left, top1, right, bottom1);
                iconDrawable.draw(c);
            }
        }
    }
}