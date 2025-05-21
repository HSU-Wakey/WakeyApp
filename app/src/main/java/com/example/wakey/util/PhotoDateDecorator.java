package com.example.wakey.util;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.example.wakey.R;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;

import java.util.Collection;
import java.util.HashSet;

public class PhotoDateDecorator implements DayViewDecorator {
    private final HashSet<CalendarDay> dates;
    private final Context context;

    public PhotoDateDecorator(Context context, Collection<CalendarDay> dates) {
        this.context = context;
        this.dates = new HashSet<>(dates);
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return dates.contains(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        // 사진이 있는 날짜를 표시하기 위해 작은 점으로 장식
        view.addSpan(new DotSpan(5, ContextCompat.getColor(context, R.color.photo_indicator)));
    }
}