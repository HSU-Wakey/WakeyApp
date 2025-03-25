// data/util/DateUtil.java
package com.example.wakey.data.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtil {

    // 날짜 포맷팅
    public static String formatDate(Date date, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(date);
    }

    // yyyy-MM-dd 포맷의 문자열 반환
    public static String getFormattedDateString(Date date) {
        return formatDate(date, "yyyy-MM-dd");
    }

    // 표시용 yyyy.MM.dd 포맷의 문자열 반환
    public static String getDisplayDateString(Date date) {
        return formatDate(date, "yyyy.MM.dd");
    }

    // 요일 포함 포맷팅
    public static String getFormattedDateWithDay(Date date) {
        return formatDate(date, "yyyy.MM.dd (E)");
    }

    // 시간 포맷팅
    public static String formatTime(Date date) {
        return formatDate(date, "HH:mm");
    }
}