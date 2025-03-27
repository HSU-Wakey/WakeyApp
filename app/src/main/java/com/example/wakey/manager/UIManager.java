package com.example.wakey.manager;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.model.SearchHistoryItem;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.SearchHistoryRepository;
import com.example.wakey.service.SearchService;
import com.example.wakey.ui.map.PlaceDetailsBottomSheet;
import com.example.wakey.ui.photo.PhotoDetailFragment;
import com.example.wakey.ui.search.SearchHistoryAdapter;
import com.example.wakey.ui.timeline.TimelineAdapter;
import com.example.wakey.ui.timeline.TimelineRenderer;
import com.example.wakey.util.ToastManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * UI 관련 기능을 관리하는 매니저 클래스
 */
public class UIManager {
    private static final String TAG = "UIManager";
    private static UIManager instance;

    private Context context;
    private Activity activity;
    private FragmentManager fragmentManager;

    // 날짜 관련 변수
    private Calendar currentSelectedDate;
    private TextView dateTextView;
    private TextView bottomSheetDateTextView;
    private OnDateChangedListener dateChangedListener;

    // 바텀 시트 관련 변수
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private RecyclerView timelineRecyclerView;
    private TimelineAdapter timelineAdapter;
    private List<TimelineItem> timelineItems = new ArrayList<>();

    // 바텀 시트 상태 관리
    public static final int BOTTOM_SHEET_HIDDEN = 0;
    public static final int BOTTOM_SHEET_HALF_EXPANDED = 1;
    public static final int BOTTOM_SHEET_EXPANDED = 2;
    public int currentBottomSheetState = BOTTOM_SHEET_HIDDEN;

    // 검색 대화상자
    private AlertDialog searchDialog;
    private OnSearchPerformedListener searchListener;

    // 인터페이스 정의
    public interface OnDateChangedListener {
        void onDateChanged(String formattedDate);
    }

    public interface OnSearchPerformedListener {
        void onSearchPerformed(String query);
    }

    public interface OnTimelineItemClickListener {
        void onTimelineItemClick(TimelineItem item, int position);
    }

    private UIManager(Context context) {
        this.context = context.getApplicationContext();
        this.currentSelectedDate = Calendar.getInstance();
    }

    /**
     * 싱글톤 인스턴스 반환
     */
    public static synchronized UIManager getInstance(Context context) {
        if (instance == null) {
            instance = new UIManager(context);
        }
        return instance;
    }

    /**
     * 초기화 메소드
     */
    public void init(Activity activity, FragmentManager fragmentManager,
                     TextView dateTextView, TextView bottomSheetDateTextView,
                     OnDateChangedListener dateChangedListener,
                     OnSearchPerformedListener searchListener) {
        this.activity = activity;
        this.fragmentManager = fragmentManager;
        this.dateTextView = dateTextView;
        this.bottomSheetDateTextView = bottomSheetDateTextView;
        this.dateChangedListener = dateChangedListener;
        this.searchListener = searchListener;

        updateDateDisplay();
    }

    /**
     * 바텀 시트 설정
     */
    public void setupBottomSheet(View bottomSheetView, OnTimelineItemClickListener listener) {
        if (bottomSheetView == null) return;

        // 바텀 시트 초기화
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);

        // 기본 상태 설정 - 앱 시작시 숨김 상태로 시작
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // 반쯤 펼쳐진 상태의 높이 설정
        int halfExpandedRatio = 50; // 화면의 50%
        bottomSheetBehavior.setHalfExpandedRatio(0.5f);

        // 드래그 가능하도록 설정
        bottomSheetBehavior.setDraggable(true);

        // 타임라인 리사이클러뷰 설정
        timelineRecyclerView = bottomSheetView.findViewById(R.id.timelineRecyclerView);
        timelineRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        timelineAdapter = new TimelineAdapter(timelineItems);
        timelineRecyclerView.setAdapter(timelineAdapter);

        // 타임라인 렌더러 추가
        timelineRecyclerView.addItemDecoration(new TimelineRenderer(context));

        // 탭 레이아웃 설정
        TabLayout tabLayout = bottomSheetView.findViewById(R.id.tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // 탭 선택 시 필터링 로직 추가 가능
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // 바텀 시트 상태 변경 리스너
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // 상태 변경 추적
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    currentBottomSheetState = BOTTOM_SHEET_HIDDEN;
                } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    currentBottomSheetState = BOTTOM_SHEET_HALF_EXPANDED;
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    currentBottomSheetState = BOTTOM_SHEET_EXPANDED;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        // 타임라인 항목 클릭 리스너 설정
        if (listener != null) {
            timelineAdapter.setOnTimelineItemClickListener((item, position) ->
                    listener.onTimelineItemClick(item, position));
        }
    }

    /**
     * 바텀 시트 상태 토글
     * 1. 숨김 -> 반쯤 펼침
     * 2. 반쯤 펼침 -> 완전히 펼침
     * 3. 완전히 펼침 -> 숨김
     */
    public void toggleBottomSheetState() {
        if (bottomSheetBehavior == null) return;

        // 현재 상태에 따라 다음 상태로 전환
        switch (currentBottomSheetState) {
            case BOTTOM_SHEET_HIDDEN:
                // 숨김 -> 반쯤 펼침
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                break;

            case BOTTOM_SHEET_HALF_EXPANDED:
                // 반쯤 펼침 -> 완전히 펼침
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                break;

            case BOTTOM_SHEET_EXPANDED:
                // 완전히 펼침 -> 반쯤 펼침 (토글 시 바로 닫히지 않고 중간 단계로)
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                break;
        }
    }

    /**
     * 바텀 시트 상태 설정
     */
    public void setBottomSheetState(int state) {
        if (bottomSheetBehavior == null) return;

        switch (state) {
            case BOTTOM_SHEET_HIDDEN:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
            case BOTTOM_SHEET_HALF_EXPANDED:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                break;
            case BOTTOM_SHEET_EXPANDED:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                break;
        }
    }

    /**
     * 타임라인 데이터 업데이트
     */
    public void updateTimelineData(List<TimelineItem> items) {
        this.timelineItems.clear();
        if (items != null) {
            this.timelineItems.addAll(items);
            Collections.sort(this.timelineItems, Comparator.comparing(TimelineItem::getTime));
        }

        if (timelineAdapter != null) {
            timelineAdapter.updateItems(this.timelineItems);
        }
    }

    /**
     * 타임라인 항목 강조
     */
    public void highlightTimelineItem(String photoPath) {
        if (timelineItems == null || timelineRecyclerView == null) return;

        // 일치하는 타임라인 항목 찾기
        for (int i = 0; i < timelineItems.size(); i++) {
            TimelineItem item = timelineItems.get(i);
            if (item.getPhotoPath() != null && item.getPhotoPath().equals(photoPath)) {
                // 해당 위치로 스크롤
                timelineRecyclerView.smoothScrollToPosition(i);
                break;
            }
        }
    }

    /**
     * 사진 세부정보 표시
     */
    public void showPhotoDetail(TimelineItem item) {
        if (fragmentManager == null || item == null) return;

        PhotoDetailFragment detailFragment = PhotoDetailFragment.newInstance(item);
        detailFragment.show(fragmentManager, "PHOTO_DETAIL");
    }

    /**
     * 장소 세부정보 바텀시트 표시
     */
    public void showPlaceDetails(String placeId) {
        if (fragmentManager == null || placeId == null) return;

        PlaceDetailsBottomSheet bottomSheet = PlaceDetailsBottomSheet.newInstance(placeId);
        bottomSheet.show(fragmentManager, "PLACE_DETAILS");
    }

    /**
     * 날짜 표시 업데이트
     */
    public void updateDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        String formattedDate = dateFormat.format(currentSelectedDate.getTime());

        if (dateTextView != null) {
            dateTextView.setText(formattedDate);
        }

        if (bottomSheetDateTextView != null) {
            bottomSheetDateTextView.setText(formattedDate);
        }
    }

    /**
     * 날짜 선택 대화상자 표시
     */
    public void showDatePickerDialog() {
        if (fragmentManager == null) return;

        // 캘린더 빌더 생성
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();

        // 커스텀 테마 적용
        builder.setTheme(R.style.CustomMaterialCalendarTheme);

        // 제목 설정 및 현재 날짜로 초기화
        builder.setTitleText("Wakey Wakey");
        builder.setSelection(currentSelectedDate.getTimeInMillis());

        // DatePicker 생성
        MaterialDatePicker<Long> materialDatePicker = builder.build();

        // 날짜 선택 리스너 설정
        materialDatePicker.addOnPositiveButtonClickListener(selection -> {
            // 선택한 날짜로 설정
            currentSelectedDate.setTimeInMillis(selection);
            updateDateDisplay();

            // 리스너 호출
            if (dateChangedListener != null) {
                dateChangedListener.onDateChanged(getFormattedDate());
            }
        });

        // 대화상자 표시
        materialDatePicker.show(fragmentManager, "DATE_PICKER");
    }

    /**
     * 이전 날짜로 변경
     */
    public void goToPreviousDate() {
        currentSelectedDate.add(Calendar.DAY_OF_MONTH, -1);
        updateDateDisplay();

        // 리스너 호출
        if (dateChangedListener != null) {
            dateChangedListener.onDateChanged(getFormattedDate());
        }
    }

    /**
     * 다음 날짜로 변경
     */
    public void goToNextDate() {
        currentSelectedDate.add(Calendar.DAY_OF_MONTH, 1);
        updateDateDisplay();

        // 리스너 호출
        if (dateChangedListener != null) {
            dateChangedListener.onDateChanged(getFormattedDate());
        }
    }

    /**
     * 형식화된 날짜 문자열 가져오기 (yyyy-MM-dd)
     */
    public String getFormattedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(currentSelectedDate.getTime());
    }

    /**
     * 현재 선택된 날짜 가져오기
     */
    public Calendar getCurrentSelectedDate() {
        return (Calendar) currentSelectedDate.clone();
    }

    /**
     * 검색 대화상자 표시 (수정된 버전)
     */
    /**
     * 검색 대화상자 표시 (오류 해결)
     */
    public void showSearchDialog() {
        if (activity == null) return;

        // 검색 서비스 초기화
        SearchService searchService = SearchService.getInstance(context);

        // 1. 대화상자 레이아웃 로드
        View searchView = LayoutInflater.from(activity).inflate(R.layout.dialog_smart_search, null);

        // 2. 검색 기록 데이터 준비
        List<SearchHistoryItem> searchHistory = SearchHistoryRepository.getInstance(context).getSearchHistory();

        // 데이터가 없으면 더미 데이터 표시
        if (searchHistory == null || searchHistory.isEmpty()) {
            searchHistory = new ArrayList<>();
            searchHistory.add(new SearchHistoryItem("서울", null, System.currentTimeMillis()));
            searchHistory.add(new SearchHistoryItem("카페", null, System.currentTimeMillis() - 3600000));
            searchHistory.add(new SearchHistoryItem("공원", null, System.currentTimeMillis() - 7200000));
        }

        // 3. RecyclerView 및 어댑터 설정
        RecyclerView recentSearchRecyclerView = searchView.findViewById(R.id.recentSearchRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recentSearchRecyclerView.setLayoutManager(layoutManager);

        SearchHistoryAdapter adapter = new SearchHistoryAdapter(searchHistory);
        recentSearchRecyclerView.setAdapter(adapter);

        // 4. 검색 기록 항목 클릭 리스너 설정
        adapter.setOnHistoryItemClickListener(item -> {
            if (searchListener != null) {
                searchListener.onSearchPerformed(item.getQuery());
            }
            if (searchDialog != null && searchDialog.isShowing()) {
                searchDialog.dismiss();
            }
        });

        // 5. 추천 검색어 설정 - 첨부된 이미지와 동일한 추천 검색어로 수정
        List<String> popularTerms = new ArrayList<>();
        popularTerms.add("광주광역시 학술대회");
        popularTerms.add("피자");
        popularTerms.add("2025년 여행");

        Chip suggestionChip1 = searchView.findViewById(R.id.suggestionChip1);
        Chip suggestionChip2 = searchView.findViewById(R.id.suggestionChip2);
        Chip suggestionChip3 = searchView.findViewById(R.id.suggestionChip3);

        if (popularTerms.size() >= 1) suggestionChip1.setText(popularTerms.get(0));
        if (popularTerms.size() >= 2) suggestionChip2.setText(popularTerms.get(1));
        if (popularTerms.size() >= 3) suggestionChip3.setText(popularTerms.get(2));

        View.OnClickListener chipClickListener = v -> {
            String chipText = ((Chip) v).getText().toString();
            if (searchListener != null) {
                searchListener.onSearchPerformed(chipText);
            }
            if (searchDialog != null && searchDialog.isShowing()) {
                searchDialog.dismiss();
            }
        };

        suggestionChip1.setOnClickListener(chipClickListener);
        suggestionChip2.setOnClickListener(chipClickListener);
        suggestionChip3.setOnClickListener(chipClickListener);

        // 6. 검색 EditText 설정
        EditText searchEditText = searchView.findViewById(R.id.searchEditText);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                String query = searchEditText.getText().toString().trim();
                if (!query.isEmpty() && searchListener != null) {
                    searchListener.onSearchPerformed(query);
                    if (searchDialog != null && searchDialog.isShowing()) {
                        searchDialog.dismiss();
                    }
                }
                return true;
            }
            return false;
        });

        // 닫기 버튼 클릭 리스너
        View closeButton = searchView.findViewById(R.id.closeButton);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                if (searchDialog != null && searchDialog.isShowing()) {
                    searchDialog.dismiss();
                }
            });
        }

        // 7. 이전 대화상자가 있으면 제거
        if (searchDialog != null && searchDialog.isShowing()) {
            searchDialog.dismiss();
            searchDialog = null;
        }

        // 8. Dialog 생성 (전체화면 테마 적용) - 주요 수정 부분
        // 1) requestWindowFeature 제거 - 이미 Dialog.Builder에서 처리됨
        // 2) 백그라운드 블러 처리 try-catch로 감싸기

        // Dialog 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.FullScreenDialogStyle);
        builder.setView(searchView);
        searchDialog = builder.create();

        // 블러 효과 및 레이아웃 설정
        if (searchDialog.getWindow() != null) {
            Window window = searchDialog.getWindow();

            // 배경 설정 - 반투명 흰색
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#BFFFFFFF")));

            // 전체 화면 레이아웃 설정
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            // 상태바까지 확장
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            // 블러 효과 (Android 12 이상만 지원)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.setBackgroundBlurRadius(25);
                } catch (Exception e) {
                    // 일부 기기에서는 지원하지 않을 수 있음
                    // 오류 무시하고 계속 진행
                }
            }

            // 페이드 인/아웃 애니메이션
            window.setWindowAnimations(R.style.DialogAnimation);
        }

        // 취소 설정
        searchDialog.setCancelable(true);
        searchDialog.setCanceledOnTouchOutside(false);

        // 뒤로가기 키 처리
        searchDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        // 9. 대화상자 표시
        try {
            searchDialog.show();
        } catch (Exception e) {
            // 예외 처리
            e.printStackTrace();
            ToastManager.getInstance().showToast("검색화면을 표시할 수 없습니다.");
            return;
        }

        // 10. 키보드 자동 표시 (지연 추가)
        searchEditText.postDelayed(() -> {
            try {
                searchEditText.requestFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception e) {
                // 키보드 표시 오류 무시
                e.printStackTrace();
            }
        }, 200);  // 지연 시간 증가
    }

    /**
     * 맵 옵션 대화상자 표시
     */
    public void showMapOptionsDialog(boolean clusteringEnabled, boolean showPOIs, int currentMapType,
                                     OnMapOptionsChangedListener listener) {
        if (activity == null) return;

        final String[] options = {
                "클러스터링 토글",
                "POI 표시 토글",
                "지도 유형 변경",
                "교통 정보 표시",
                "대중교통 표시",
                "자전거 경로 표시"
        };

        final boolean[] states = {
                clusteringEnabled,
                showPOIs,
                false, // 임시 값, 별도 처리
                false,
                false,
                false
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("지도 옵션");

        builder.setMultiChoiceItems(options, states, (dialog, which, isChecked) -> {
            if (listener != null) {
                switch (which) {
                    case 0: // 클러스터링
                        listener.onClusteringToggled(isChecked);
                        break;
                    case 1: // POI
                        listener.onPOIsToggled(isChecked);
                        break;
                    case 3: // 교통 정보
                        listener.onTrafficToggled(isChecked);
                        break;
                    case 4: // 대중교통
                        listener.onTransitToggled(isChecked);
                        break;
                }
            }
        });

        builder.setPositiveButton("확인", (dialog, which) -> {
            if (listener != null) {
                listener.onOptionsApplied();
            }
        });

        builder.setNeutralButton("지도 유형", (dialog, which) -> {
            showMapTypeDialog(currentMapType, listener);
        });

        builder.show();
    }

    /**
     * 지도 유형 선택 대화상자 표시
     */
    private void showMapTypeDialog(int currentMapType, OnMapOptionsChangedListener listener) {
        if (activity == null) return;

        final String[] mapTypes = {
                "일반",
                "위성",
                "지형",
                "하이브리드"
        };

        int currentType = 0;
        switch (currentMapType) {
            case 1: // MAP_TYPE_NORMAL
                currentType = 0;
                break;
            case 2: // MAP_TYPE_SATELLITE
                currentType = 1;
                break;
            case 3: // MAP_TYPE_TERRAIN
                currentType = 2;
                break;
            case 4: // MAP_TYPE_HYBRID
                currentType = 3;
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("지도 유형 선택");
        builder.setSingleChoiceItems(mapTypes, currentType, (dialog, which) -> {
            if (listener != null) {
                int selectedType = 1; // MAP_TYPE_NORMAL
                switch (which) {
                    case 1:
                        selectedType = 2; // MAP_TYPE_SATELLITE
                        break;
                    case 2:
                        selectedType = 3; // MAP_TYPE_TERRAIN
                        break;
                    case 3:
                        selectedType = 4; // MAP_TYPE_HYBRID
                        break;
                }
                listener.onMapTypeChanged(selectedType);
            }
            dialog.dismiss();
        });

        builder.show();
    }

    /**
     * 맵 옵션 변경 리스너 인터페이스
     */
    public interface OnMapOptionsChangedListener {
        void onClusteringToggled(boolean enabled);

        void onPOIsToggled(boolean show);

        void onMapTypeChanged(int mapType);

        void onTrafficToggled(boolean enabled);

        void onTransitToggled(boolean enabled);

        void onOptionsApplied();
    }

    /**
     * 현재 바텀 시트 상태 가져오기
     */
    public int getCurrentBottomSheetState() {
        return currentBottomSheetState;
    }

    /**
     * 날짜 설정
     */
    public void setDate(Date date) {
        if (date != null) {
            currentSelectedDate.setTime(date);
            updateDateDisplay();

            if (dateChangedListener != null) {
                dateChangedListener.onDateChanged(getFormattedDate());
            }
        }
    }

    /**
     * 토스트 메시지 표시
     */
    public void showToast(String message) {
        ToastManager.getInstance().showToast(message);
    }
}