package com.example.wakey.ui.timeline;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.example.wakey.service.ClusterService;
import com.example.wakey.util.PhotoDateDecorator;
import com.google.android.gms.maps.model.LatLng;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;

import org.threeten.bp.LocalDate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class TimelineFragment extends Fragment {

    private RecyclerView recyclerView;
    private TimelineAdapter adapter;
    private static final String TAG = "TimelineDebug";

    // 캘린더 관련 UI 요소
    private MaterialCalendarView calendarView;
    private TextView dateRangeTextView;
    private Button resetButton;
    private TextView noItemsTextView;

    // 선택된 날짜 관련 변수들
    private CalendarDay firstSelectedDate = null;
    private CalendarDay lastSelectedDate = null;
    private long lastClickTime = 0;
    private CalendarDay lastClickedDate = null;

    // 더블 클릭 감지 시간 (300ms)
    private static final long DOUBLE_CLICK_TIME_DELTA = 300;

    private PhotoRepository photoRepository;
    private ClusterService clusterService;
    private List<PhotoInfo> allPhotoInfoList = new ArrayList<>();
    private TimelineManager timelineManager;

    public TimelineFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // 새로운 레이아웃 사용 (캘린더 포함)
        View view = inflater.inflate(R.layout.fragment_timeline_calendar, container, false);

        // UI 요소 초기화
        initViews(view);
        setupRecyclerView();

        // TimelineManager 초기화
        timelineManager = TimelineManager.getInstance(requireContext());

        // 캘린더 설정 (사진 로드 후)
        loadPhotosAndSetupCalendar();

        // 초기화 버튼 클릭 리스너
        resetButton.setOnClickListener(v -> resetSelection());

        // 디버그 버튼 추가
        Button debugButton = new Button(requireContext());
        debugButton.setText("선택 상태 확인");
        debugButton.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        ((ViewGroup) view).addView(debugButton);

        debugButton.setOnClickListener(v -> {
            String status = "첫 번째 날짜: " + (firstSelectedDate != null ? firstSelectedDate.toString() : "없음") +
                    "\n마지막 날짜: " + (lastSelectedDate != null ? lastSelectedDate.toString() : "없음");
            Log.e(TAG, status);
            Toast.makeText(requireContext(), status, Toast.LENGTH_LONG).show();

            // 선택된 날짜들 확인
            List<CalendarDay> selectedDates = calendarView.getSelectedDates();
            Log.e(TAG, "선택된 날짜 수: " + selectedDates.size());
            for (CalendarDay day : selectedDates) {
                Log.e(TAG, "선택된 날짜: " + day);
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "TimelineFragment onViewCreated 호출됨");
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.timelineRecyclerView);
        calendarView = view.findViewById(R.id.calendarView);
        dateRangeTextView = view.findViewById(R.id.dateRangeTextView);
        resetButton = view.findViewById(R.id.resetButton);
        noItemsTextView = view.findViewById(R.id.noItemsTextView);

        // 테스트 버튼 참조 추가
        Button testDateRangeButton = view.findViewById(R.id.testDateRangeButton);
        if (testDateRangeButton != null) {
            testDateRangeButton.setOnClickListener(v -> {
                Log.d(TAG, "테스트 버튼 클릭됨");
                testDateRangeSelection();
            });
        } else {
            Log.e(TAG, "❌ 테스트 버튼을 찾을 수 없음");
        }
    }
    // 테스트 메서드 분리
    private void testDateRangeSelection() {
        try {
            // 테스트 - 현재 날짜와 일주일 후 날짜로 범위 설정
            CalendarDay today = CalendarDay.today();
            Log.e(TAG, "오늘 날짜: " + today);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 7);
            CalendarDay nextWeek = CalendarDay.from(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
            Log.e(TAG, "일주일 후: " + nextWeek);

            // 완전 초기화
            calendarView.clearSelection();

            // UI 및 내부 상태 초기화
            firstSelectedDate = null;
            lastSelectedDate = null;

            // 첫 번째 날짜 선택 (UI만)
            calendarView.setDateSelected(today, true);
            firstSelectedDate = today;

            // 잠시 대기 (UI 업데이트 시간)
            new Handler().postDelayed(() -> {
                // 두 번째 날짜 선택 (UI만)
                calendarView.setDateSelected(nextWeek, true);
                lastSelectedDate = nextWeek;

                // 모든 중간 날짜 선택
                selectDateRange(firstSelectedDate, lastSelectedDate);

                // UI 업데이트
                updateDateRangeText();
                filterAndDisplayPhotos();

                Log.e(TAG, "테스트 완료: " + today + " ~ " + nextWeek);
                Toast.makeText(requireContext(),
                        "테스트: " + today.getMonth() + "/" + today.getDay() + " ~ " +
                                nextWeek.getMonth() + "/" + nextWeek.getDay() + " 기간의 사진을 표시합니다",
                        Toast.LENGTH_LONG).show();
            }, 300); // 300ms 대기

        } catch (Exception e) {
            Log.e(TAG, "테스트 실행 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

//    private void testDateRangeSelection() {
//        try {
//            // 테스트 - 현재 날짜와 일주일 후 날짜로 범위 설정
//            CalendarDay today = CalendarDay.today();
//            Log.e(TAG, "오늘 날짜: " + today);
//
//            Calendar cal = Calendar.getInstance();
//            cal.add(Calendar.DAY_OF_MONTH, 7);
//            CalendarDay nextWeek = CalendarDay.from(
//                    cal.get(Calendar.YEAR),
//                    cal.get(Calendar.MONTH) + 1,
//                    cal.get(Calendar.DAY_OF_MONTH));
//            Log.e(TAG, "일주일 후: " + nextWeek);
//
//            // 완전 초기화
//            calendarView.clearSelection();
//
//            // 선택 로직 직접 실행
//            firstSelectedDate = today;
//            lastSelectedDate = nextWeek;
//
//            // UI 업데이트
//            selectDateRange(firstSelectedDate, lastSelectedDate);
//            updateDateRangeText();
//            filterAndDisplayPhotos();
//
//            Log.e(TAG, "테스트 완료: " + today + " ~ " + nextWeek);
//
//            Toast.makeText(requireContext(),
//                    "테스트: " + today.getMonth() + "/" + today.getDay() + " ~ " +
//                            nextWeek.getMonth() + "/" + nextWeek.getDay() + " 기간의 사진을 표시합니다",
//                    Toast.LENGTH_LONG).show();
//        } catch (Exception e) {
//            Log.e(TAG, "테스트 실행 중 오류: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TimelineAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);
    }

    private void loadPhotosAndSetupCalendar() {
        showLoading(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            photoRepository = PhotoRepository.getInstance(requireContext());
            List<Photo> photoEntities = photoRepository.getPhotosWithObjects();
            clusterService = ClusterService.getInstance(requireContext());

            allPhotoInfoList = convertToPhotoInfo(photoEntities);

            // UI 스레드에서 캘린더 설정
            new Handler(Looper.getMainLooper()).post(() -> {
                setupCalendar();

                // 기본 타임라인 데이터 로드 (전체 사진)
                List<TimelineItem> timelineItems = clusterService.generateTimelineFromPhotoList(allPhotoInfoList);
                adapter.updateData(timelineItems);

                showLoading(false);

                if (timelineItems.isEmpty()) {
                    showNoItemsMessage(true);
                } else {
                    showNoItemsMessage(false);
                }
            });
        });
    }

    private List<PhotoInfo> convertToPhotoInfo(List<Photo> photoEntities) {
        List<PhotoInfo> photoInfoList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());

        for (Photo photo : photoEntities) {
            LatLng latLng = new LatLng(photo.latitude, photo.longitude);

            Date date;
            try {
                date = sdf.parse(photo.dateTaken);
            } catch (ParseException e) {
                Log.e(TAG, "날짜 파싱 실패: " + photo.dateTaken, e);
                date = new Date();
            }

            List<String> objects = new ArrayList<>();
            if (photo.detectedObjects != null && !photo.detectedObjects.isEmpty()) {
                objects = Arrays.asList(photo.detectedObjects.split(","));
            }

            String address = photo.locationDo + " " + photo.locationGu + " " + photo.locationStreet;

            PhotoInfo info = new PhotoInfo(
                    photo.filePath,
                    date,
                    latLng,
                    null,
                    null,
                    address,
                    photo.caption,
                    objects
            );

            info.setLocationDo(photo.locationDo);
            info.setLocationGu(photo.locationGu);
            info.setLocationStreet(photo.locationStreet);

            photoInfoList.add(info);
        }

        return photoInfoList;
    }

    private void setupCalendar() {
        // 캘린더 초기 설정
        calendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_RANGE); // 범위 선택 모드로 변경
        calendarView.setShowOtherDates(MaterialCalendarView.SHOW_ALL);
        calendarView.setClickable(true);
        calendarView.setFocusable(true);

        // 명시적으로 오늘 날짜 강조
        calendarView.setCurrentDate(CalendarDay.today());

        // 중요: 모든 기존 리스너와 데코레이터를 제거 (초기화)
        calendarView.removeDecorators();

        // 날짜에 사진이 있는지 표시하는 데코레이터 추가
        PhotoDateDecorator photoDecorator = new PhotoDateDecorator(requireContext(), getPhotoDateList());
        calendarView.addDecorator(photoDecorator);

        // 날짜 클릭 리스너 설정 - 커스텀 날짜 선택 로직 구현
        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            Log.e(TAG, "날짜 선택됨: " + date + ", selected: " + selected);

            if (selected) {
                // 날짜가 선택되었을 때
                if (firstSelectedDate == null) {
                    // 첫 번째 날짜 선택
                    firstSelectedDate = date;
                    lastSelectedDate = null;

                    // UI에 표시
                    calendarView.clearSelection();
                    calendarView.setDateSelected(date, true);

                    Toast.makeText(requireContext(),
                            "시작일: " + date.getMonth() + "월 " + date.getDay() + "일",
                            Toast.LENGTH_SHORT).show();
                } else if (lastSelectedDate == null) {
                    // 두 번째 날짜 선택 - 범위 설정
                    lastSelectedDate = date;

                    // 시작일과 종료일 순서 확인
                    if (lastSelectedDate.isBefore(firstSelectedDate)) {
                        CalendarDay temp = firstSelectedDate;
                        firstSelectedDate = lastSelectedDate;
                        lastSelectedDate = temp;
                    }

                    // 범위 내 모든 날짜 선택
                    selectDateRange(firstSelectedDate, lastSelectedDate);

                    // UI 업데이트
                    updateDateRangeText();
                    filterAndDisplayPhotos();

                    Toast.makeText(requireContext(),
                            "기간: " + firstSelectedDate.getMonth() + "월 " + firstSelectedDate.getDay() + "일 ~ " +
                                    lastSelectedDate.getMonth() + "월 " + lastSelectedDate.getDay() + "일",
                            Toast.LENGTH_SHORT).show();
                } else {
                    // 이미 범위가 설정된 상태에서 새로운 선택 - 리셋 후 새 시작일 설정
                    firstSelectedDate = date;
                    lastSelectedDate = null;

                    // UI에 표시
                    calendarView.clearSelection();
                    calendarView.setDateSelected(date, true);

                    Toast.makeText(requireContext(),
                            "새 시작일: " + date.getMonth() + "월 " + date.getDay() + "일",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        Log.e(TAG, "캘린더 설정 완료 - 커스텀 날짜 선택 로직 적용");
    }

//        // 범위 선택 리스너 추가
//        calendarView.setOnRangeSelectedListener((widget, dates) -> {
//            Log.e(TAG, "범위 선택됨: " + dates.size() + "개 날짜");
//
//            if (dates.size() > 0) {
//                CalendarDay first = dates.get(0);
//                CalendarDay last = dates.get(dates.size() - 1);
//
//                Log.d(TAG, "날짜 범위 선택됨: " + first + " ~ " + last);
//
//                firstSelectedDate = first;
//                lastSelectedDate = last;
//
//                updateDateRangeText();
//                filterAndDisplayPhotos();
//
//                Toast.makeText(requireContext(),
//                        "기간: " + firstSelectedDate.getMonth() + "월 " + firstSelectedDate.getDay() + "일 ~ " +
//                                lastSelectedDate.getMonth() + "월 " + lastSelectedDate.getDay() + "일의 사진을 표시합니다",
//                        Toast.LENGTH_SHORT).show();
//            }
//        });

//        // 단일 날짜 선택 리스너도 유지 (하루만 선택할 경우 사용)
//        calendarView.setOnDateSelectedListener(new OnDateSelectedListener() {
//            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
//                Log.e(TAG, "날짜 선택됨: " + date + ", selected: " + selected);
//
//                // 단일 날짜 선택 시 처리 로직
//                if (selected) {
//                    firstSelectedDate = date;
//                    lastSelectedDate = date;  // 동일한 날짜로 설정 (하루)
//
//                    updateDateRangeText();
//                    filterAndDisplayPhotos();
//
//                    Toast.makeText(requireContext(),
//                            date.getMonth() + "월 " + date.getDay() + "일 하루의 사진을 표시합니다",
//                            Toast.LENGTH_SHORT).show();
//                }
//            }
//        });

//        // 날짜 변경 리스너 설정
//        // Material Calendar View에서는 하나의 리스너만 설정 가능
//        calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
//            @Override
//            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
//                Log.e(TAG, "날짜 선택됨: " + date + ", selected: " + selected);
//
//                // 선택 모드가 RANGE이므로, 선택한 날짜의 상태에 따라 처리
//                if (selected) {
//                    if (firstSelectedDate == null) {
//                        // 첫 번째 날짜 선택
//                        firstSelectedDate = date;
//                        Log.e(TAG, "첫 번째 날짜 선택: " + date);
//                        Toast.makeText(requireContext(),
//                                "시작일: " + date.getMonth() + "월 " + date.getDay() + "일",
//                                Toast.LENGTH_SHORT).show();
//                    } else if (lastSelectedDate == null) {
//                        // 두 번째 날짜 선택 (범위 완성)
//                        if (date.isBefore(firstSelectedDate)) {
//                            lastSelectedDate = firstSelectedDate;
//                            firstSelectedDate = date;
//                        } else {
//                            lastSelectedDate = date;
//                        }
//                        Log.e(TAG, "범위 선택 완료: " + firstSelectedDate + " ~ " + lastSelectedDate);
//                        updateDateRangeText();
//                        filterAndDisplayPhotos();
//
//                        Toast.makeText(requireContext(),
//                                "기간: " + firstSelectedDate.getMonth() + "월 " + firstSelectedDate.getDay() + "일 ~ " +
//                                        lastSelectedDate.getMonth() + "월 " + lastSelectedDate.getDay() + "일의 사진을 표시합니다",
//                                Toast.LENGTH_SHORT).show();
//                    }
//                } else {
//                    // 날짜 선택 해제 시
//                    if (lastSelectedDate != null) {
//                        // 범위 선택 중 마지막 날짜 해제
//                        lastSelectedDate = null;
//                    } else if (firstSelectedDate != null) {
//                        // 첫 날짜만 선택된 상태에서 해제
//                        firstSelectedDate = null;
//                    }
//                }
//            }
//        });
//
//        // MaterialCalendarView의 상태 변경 리스너 추가 (SELECTION_MODE_RANGE 모드에서 범위 선택 시 호출됨)
//        calendarView.setOnMonthChangedListener((widget, date) -> {
//            Log.e(TAG, "달력 월 변경: " + date);
//        });
//
//        Log.e(TAG, "캘린더 설정 완료 - SELECTION_MODE_RANGE 적용됨");
//    }

//    private void handleDateSelection(CalendarDay selectedDate) {
//
//        Log.d(TAG, "handleDateSelection: 날짜 선택 처리 시작");
//        Log.d(TAG, "=== handleDateSelection 호출됨 ===");
//        Log.d(TAG, "selectedDate: " + selectedDate);
//        Log.d(TAG, "firstSelectedDate: " + firstSelectedDate);
//        Log.d(TAG, "lastSelectedDate: " + lastSelectedDate);
//        Log.d(TAG, "lastClickedDate: " + lastClickedDate);
//        Log.d(TAG, "lastClickTime: " + lastClickTime);
//        Log.d(TAG, "현재 시간: " + System.currentTimeMillis());
//
//        long clickTime = System.currentTimeMillis();
//
//        // 같은 날짜 더블 클릭 체크
//        boolean isDoubleClick = lastClickedDate != null &&
//                lastClickedDate.equals(selectedDate) &&
//                clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA;
//
//        if (isDoubleClick) {
//            Log.d(TAG, "더블 클릭 감지: " + selectedDate);
//            // 더블 클릭: 해당 날짜만 선택
//            calendarView.clearSelection();
//            calendarView.setDateSelected(selectedDate, true);
//
//            firstSelectedDate = selectedDate;
//            lastSelectedDate = selectedDate;
//
//            updateDateRangeText();
//            filterAndDisplayPhotos();
//
//            Toast.makeText(requireContext(),
//                    selectedDate.getMonth() + "월 " + selectedDate.getDay() + "일 하루의 사진을 표시합니다",
//                    Toast.LENGTH_SHORT).show();
//        } else {
//            // 첫 번째 선택이거나 이미 범위가 선택된 상태에서 새 선택 시작
//            if (firstSelectedDate == null || lastSelectedDate != null) {
//                Log.d(TAG, "첫 번째 날짜 선택: " + selectedDate);
//                calendarView.clearSelection();
//                calendarView.setDateSelected(selectedDate, true);
//
//                firstSelectedDate = selectedDate;
//                lastSelectedDate = null;
//
//                Toast.makeText(requireContext(),
//                        "시작일: " + selectedDate.getMonth() + "월 " + selectedDate.getDay() + "일",
//                        Toast.LENGTH_SHORT).show();
//            } else {
//                Log.d(TAG, "두 번째 날짜 선택: " + selectedDate);
//                // 두 번째 날짜 선택 (범위 완성)
//                if (selectedDate.isBefore(firstSelectedDate)) {
//                    // 시작일보다 앞선 날짜가 선택된 경우, 순서 변경
//                    lastSelectedDate = firstSelectedDate;
//                    firstSelectedDate = selectedDate;
//                } else {
//                    lastSelectedDate = selectedDate;
//                }
//
//                // 범위 내 모든 날짜 선택 표시
//                selectDateRange(firstSelectedDate, lastSelectedDate);
//                updateDateRangeText();
//                filterAndDisplayPhotos();
//
//                Toast.makeText(requireContext(),
//                        "기간: " + firstSelectedDate.getMonth() + "월 " + firstSelectedDate.getDay() + "일 ~ " +
//                                lastSelectedDate.getMonth() + "월 " + lastSelectedDate.getDay() + "일의 사진을 표시합니다",
//                        Toast.LENGTH_SHORT).show();
//            }
//        }
//
//        // 마지막 클릭 정보 갱신
//        lastClickedDate = selectedDate;
//        lastClickTime = clickTime;
//    }

    private void selectDateRange(CalendarDay startDate, CalendarDay endDate) {
        calendarView.clearSelection();
        Log.d(TAG, "날짜 범위 선택: " + startDate + " ~ " + endDate);

        // 시작일부터 종료일까지 모든 날짜 선택
        Calendar calendar = Calendar.getInstance();
        calendar.set(startDate.getYear(), startDate.getMonth() - 1, startDate.getDay());

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.set(endDate.getYear(), endDate.getMonth() - 1, endDate.getDay());

        while (!calendar.getTime().after(endCalendar.getTime())) {
            CalendarDay day = CalendarDay.from(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH));

            calendarView.setDateSelected(day, true);
            calendar.add(Calendar.DATE, 1);
        }
    }

    private void updateDateRangeText() {
        if (firstSelectedDate != null && lastSelectedDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA);

            Date firstDate = getDateFromCalendarDay(firstSelectedDate);
            Date lastDate = getDateFromCalendarDay(lastSelectedDate);

            if (firstDate.equals(lastDate)) {
                // 단일 날짜 선택
                dateRangeTextView.setText(dateFormat.format(firstDate) + " 하루 동안의 사진");
            } else {
                // 날짜 범위 선택
                dateRangeTextView.setText(dateFormat.format(firstDate) + " ~ " +
                        dateFormat.format(lastDate) + " 기간의 사진");
            }

            dateRangeTextView.setVisibility(View.VISIBLE);
        } else {
            dateRangeTextView.setVisibility(View.GONE);
        }
    }

    private void filterAndDisplayPhotos() {
        if (firstSelectedDate != null && lastSelectedDate != null) {
            Date startDate = getDateFromCalendarDay(firstSelectedDate);
            Date endDate = getDateFromCalendarDay(lastSelectedDate);

            // 시간 정보 조정 (시작일은 0시 0분, 종료일은 23시 59분)
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            endDate = calendar.getTime();

            final Date finalStartDate = startDate;
            final Date finalEndDate = endDate;

            // 로딩 상태 표시
            showLoading(true);

            // 백그라운드 스레드에서 필터링 작업 수행
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // TimelineManager를 통해 날짜 범위 항목 가져오기
                    List<TimelineItem> timelineItems = timelineManager.getTimelineItemsForDateRange(finalStartDate, finalEndDate);
                    Log.d(TAG, "필터링된 타임라인 항목: " + timelineItems.size() + "개");

                    // UI 스레드에서 어댑터 업데이트
                    new Handler(Looper.getMainLooper()).post(() -> {
                        adapter.updateData(timelineItems);
                        showLoading(false);

                        // 결과가 없을 경우 메시지 표시
                        if (timelineItems.isEmpty()) {
                            showNoItemsMessage(true);
                        } else {
                            showNoItemsMessage(false);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "필터링 중 오류 발생: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        showLoading(false);
                        Toast.makeText(requireContext(), "사진 로드 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            // 날짜 범위가 선택되지 않은 경우
            adapter.updateData(new ArrayList<>());
            showNoItemsMessage(false);
        }
    }

    private void resetSelection() {
        calendarView.clearSelection();
        firstSelectedDate = null;
        lastSelectedDate = null;
        lastClickedDate = null;
        dateRangeTextView.setVisibility(View.GONE);
        showNoItemsMessage(false);

        // 모든 사진으로 타임라인 리셋
        showLoading(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            List<TimelineItem> timelineItems = clusterService.generateTimelineFromPhotoList(allPhotoInfoList);
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateData(timelineItems);
                showLoading(false);

                if (timelineItems.isEmpty()) {
                    showNoItemsMessage(true);
                }

                Toast.makeText(requireContext(), "선택이 초기화되었습니다", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showLoading(boolean isLoading) {
        // View가 없거나 이미 detach된 경우 처리
        if (getView() == null) return;

        View progressBar = getView().findViewById(R.id.progressBar);
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    private void showNoItemsMessage(boolean show) {
        if (noItemsTextView != null) {
            noItemsTextView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                noItemsTextView.setText("선택한 기간에 사진이 없습니다");
            }
        }
    }

    private Date getDateFromCalendarDay(CalendarDay calendarDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(calendarDay.getYear(), calendarDay.getMonth() - 1, calendarDay.getDay(), 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private List<CalendarDay> getPhotoDateList() {
        // 사진이 있는 날짜 목록 생성
        List<CalendarDay> dates = new ArrayList<>();
        Map<String, Boolean> dateMap = new HashMap<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        for (PhotoInfo photo : allPhotoInfoList) {
            Date photoDate = photo.getDateTaken();
            if (photoDate != null) {
                String dateString = dateFormat.format(photoDate);
                if (!dateMap.containsKey(dateString)) {
                    dateMap.put(dateString, true);

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(photoDate);

                    CalendarDay day = CalendarDay.from(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH));

                    dates.add(day);
                }
            }
        }

        return dates;
    }
}