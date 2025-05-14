package com.example.wakey.manager;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.SearchHistoryItem;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.SearchHistoryRepository;
import com.example.wakey.data.util.SimilarityUtil;
import com.example.wakey.service.SearchService;
import com.example.wakey.tflite.ClipTextEncoder;
import com.example.wakey.tflite.ClipTokenizer;
import com.example.wakey.ui.map.PlaceDetailsBottomSheet;
import com.example.wakey.ui.photo.PhotoDetailFragment;
import com.example.wakey.ui.search.SearchActivity;
import com.example.wakey.ui.search.SearchHistoryAdapter;
import com.example.wakey.ui.search.SearchResultAdapter;
import com.example.wakey.ui.timeline.StoryAdapter;
import com.example.wakey.ui.timeline.StoryFragment;
import com.example.wakey.ui.timeline.TimelineAdapter;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.ui.timeline.TimelineRenderer;
import com.example.wakey.util.ToastManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

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

    // 스토리 관련 변수 (추가)
    private RecyclerView storyRecyclerView;
    private StoryAdapter storyAdapter;
    private TabLayout tabLayout;
    private boolean isTimelineTabSelected = true; // 현재 선택된 탭 (기본: 타임라인)

    private List<TimelineItem> timelineItems = new ArrayList<>();

    // 바텀 시트 상태 관리
    public static final int BOTTOM_SHEET_HIDDEN = 0;
    public static final int BOTTOM_SHEET_HALF_EXPANDED = 1;
    public static final int BOTTOM_SHEET_EXPANDED = 2;
    public int currentBottomSheetState = BOTTOM_SHEET_HIDDEN;

    // 검색 대화상자
    private AlertDialog searchDialog;
    private OnSearchPerformedListener searchListener;
    private OnSearchQueryListener searchQueryListener;

    // 인터페이스 정의
    public interface OnDateChangedListener {
        void onDateChanged(String formattedDate);
    }

    // 검색 결과 인터페이스
    public interface OnSearchPerformedListener {
        void onSearchPerformed(String query);
    }

    public interface OnTimelineItemClickListener {
        void onTimelineItemClick(TimelineItem item, int position);
    }

    // 검색 관련 인터페이스
    public interface OnSearchQueryListener {
        void onSearch(String query);
    }

//    private OnSearchPerformedListener searchListener;

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
                     OnSearchQueryListener searchQueryListener) {
        this.activity = activity;
        this.fragmentManager = fragmentManager;
        this.dateTextView = dateTextView;
        this.bottomSheetDateTextView = bottomSheetDateTextView;
        this.dateChangedListener = dateChangedListener;
        this.searchQueryListener = searchQueryListener;

        updateDateDisplay();
    }

    /**
     * 초기화 메소드 (검색 기능 추가)
     */
    public void initWithSearchPerformer(Activity activity, FragmentManager fragmentManager,
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

        // 스토리 리사이클러뷰 설정 (추가)
        storyRecyclerView = bottomSheetView.findViewById(R.id.storyRecyclerView);
        if (storyRecyclerView != null) {
            storyRecyclerView.setLayoutManager(new LinearLayoutManager(context));
            storyAdapter = new StoryAdapter(timelineItems);
            storyRecyclerView.setAdapter(storyAdapter);

            TimelineManager.getInstance(context).setStoryAdapter(storyAdapter);

            // 초기 상태는 숨김
            storyRecyclerView.setVisibility(View.GONE);

            // 스토리 아이템 클릭 리스너
            storyAdapter.setOnItemClickListener((item, position) -> {
                if (listener != null) {
                    listener.onTimelineItemClick(item, position);
                }
            });
        }

        // 탭 레이아웃 설정
        tabLayout = bottomSheetView.findViewById(R.id.tab_layout);

// 기본 탭을 명시적으로 타임라인(0번 탭)으로 설정
        TabLayout.Tab timelineTab = tabLayout.getTabAt(0);
        if (timelineTab != null) {
            timelineTab.select();
        }
        timelineRecyclerView.setVisibility(View.VISIBLE);
        if (storyRecyclerView != null) {
            storyRecyclerView.setVisibility(View.GONE);
        }
        isTimelineTabSelected = true;

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                Log.d("UIManager", "탭 선택됨: " + position);

                if (position == 0) {
                    // 타임라인 탭 선택
                    Log.d("UIManager", "타임라인 탭 표시, 아이템 수: " + timelineItems.size());
                    timelineRecyclerView.setVisibility(View.VISIBLE);
                    if (storyRecyclerView != null) {
                        storyRecyclerView.setVisibility(View.GONE);
                    }
                    isTimelineTabSelected = true;
                } else if (position == 1) {
                    // 스토리 탭 선택
                    Log.d("UIManager", "스토리 탭 표시, 아이템 수: " + timelineItems.size());
                    timelineRecyclerView.setVisibility(View.GONE);
                    if (storyRecyclerView != null) {
                        storyRecyclerView.setVisibility(View.VISIBLE);

                        // 스토리 상태 로깅
                        for (TimelineItem item : timelineItems) {
                            Log.d("UIManager", "스토리 항목: " + item.getPhotoPath() +
                                    ", 스토리: " + (item.getStory() != null ? item.getStory() : "null"));
                        }
                    }
                    isTimelineTabSelected = false;
                }
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

        if (currentBottomSheetState == state) {
            return; // 변경 없음
        }

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

        currentBottomSheetState = state;
        Log.d("BOTTOM_SHEET", "🔄 바텀시트 상태 변경: " + state);
    }

    /**
     * 타임라인 데이터 업데이트
     */
    public void updateTimelineData(List<TimelineItem> items) {
        if (items == null) return;

        this.timelineItems.clear();
        this.timelineItems.addAll(items);
        Collections.sort(this.timelineItems, Comparator.comparing(TimelineItem::getTime));

        Log.d("TIMELINE_UI", "🔄 타임라인 갱신: " + items.size() + "개 항목");

        // 타임라인 어댑터 업데이트
        if (timelineAdapter != null) {
            timelineAdapter.updateItems(this.timelineItems);
        }

        // 스토리 어댑터 업데이트 (추가)
        if (storyAdapter != null) {
            storyAdapter.updateItems(this.timelineItems);
        }

        // 데이터가 있으면 바텀 시트 표시
        if (!this.timelineItems.isEmpty() && currentBottomSheetState == BOTTOM_SHEET_HIDDEN) {
            setBottomSheetState(BOTTOM_SHEET_HALF_EXPANDED);
        }
    }

    /**
     * 타임라인 항목 강조 표시
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
     * 장소 세부정보 표시
     */
    public void showPlaceDetails(String placeId) {
        if (fragmentManager == null || placeId == null) return;

        PlaceDetailsBottomSheet bottomSheet = PlaceDetailsBottomSheet.newInstance(placeId);
        bottomSheet.show(fragmentManager, "PLACE_DETAILS");
    }

    /**
     * 날짜 표시 업데이트
     */
    private void updateDateDisplay() {
        SimpleDateFormat yearMonthDayFormat = new SimpleDateFormat("yyyy.M.d", Locale.getDefault());
        SimpleDateFormat shortDateFormat = new SimpleDateFormat("yyyy.M.d", Locale.getDefault());  // 간단한 형식도 수정

        if (dateTextView != null) {
            dateTextView.setText(yearMonthDayFormat.format(currentSelectedDate.getTime()));
        }
        if (bottomSheetDateTextView != null) {
            bottomSheetDateTextView.setText(shortDateFormat.format(currentSelectedDate.getTime()));
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
     * 이전 날짜로 이동
     */
    public void goToPreviousDate() {
        currentSelectedDate.add(Calendar.DAY_OF_MONTH, -1);
        updateDateDisplay();
        notifyDateChanged();

        // 리스너 호출
        if (dateChangedListener != null) {
            dateChangedListener.onDateChanged(getFormattedDate());
        }
    }

    /**
     * 다음 날짜로 이동
     */
    public void goToNextDate() {
        currentSelectedDate.add(Calendar.DAY_OF_MONTH, 1);
        updateDateDisplay();
        notifyDateChanged();
    }

    /**
     * 날짜 변경 시 호출
     */
    private void notifyDateChanged() {
        if (dateChangedListener != null) {
            dateChangedListener.onDateChanged(getFormattedDate());
        }
    }

    /**
     * 포맷된 날짜 문자열 반환
     */
    public String getFormattedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(currentSelectedDate.getTime());
    }

    /**
     * 날짜 설정
     */
    public Calendar getCurrentSelectedDate() {
        return (Calendar) currentSelectedDate.clone();
    }

    public void setDate(Date date) {
        if (date != null) {
            currentSelectedDate.setTime(date);
            updateDateDisplay();
            notifyDateChanged();
        }
    }

    /**
     * 검색 대화상자 표시
     */
    public void showSearchDialog() {
        if (activity == null) return;
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 검색 서비스 초기화
        SearchService searchService = SearchService.getInstance(context);

        // 1. 대화상자 레이아웃 로드
        View searchView = LayoutInflater.from(activity).inflate(R.layout.dialog_smart_search, null);

        // 2. 검색 EditText 설정
        EditText searchEditText = searchView.findViewById(R.id.searchEditText);
        TextView resultTextView = searchView.findViewById(R.id.searchResultTextView);
        TextView resultCountTextView = searchView.findViewById(R.id.resultCountTextView);

        // ViewPager2 대신 RecyclerView 사용 - 격자형 레이아웃으로 변경
        RecyclerView resultGridRecyclerView = searchView.findViewById(R.id.resultGridRecyclerView);

        // 격자형 레이아웃 매니저 설정 (3열)
        GridLayoutManager layoutManager = new GridLayoutManager(activity, 3);
        resultGridRecyclerView.setLayoutManager(layoutManager);

        // 어댑터 초기화
        SearchResultAdapter adapter = new SearchResultAdapter(new ArrayList<>());
        resultGridRecyclerView.setAdapter(adapter);

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                String query = searchEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    resultTextView.setText("🔍 \"" + query + "\" 검색 중...");

                    // UI 요소 숨기기
                    resultCountTextView.setVisibility(View.GONE);
                    resultGridRecyclerView.setVisibility(View.GONE);

                    new Thread(() -> {
                        try {
                            // 1. 텍스트 → 벡터
                            ClipTokenizer tokenizer = new ClipTokenizer(context);
                            int[] tokenIds = tokenizer.tokenize(query);
                            ClipTextEncoder encoder = new ClipTextEncoder(context);
                            float[] textVec = encoder.getTextEncoding(tokenIds);
                            encoder.close();

                            // 2. DB에서 이미지 벡터 가져와 유사도 비교
                            List<Photo> photoList = AppDatabase.getInstance(context).photoDao().getAllPhotos();
                            List<SearchResultAdapter.SearchResult> allResults = new ArrayList<>();

                            for (Photo photo : photoList) {
                                float[] imageVec = photo.getEmbeddingVector();
                                if (imageVec == null) continue;

                                float sim = SimilarityUtil.cosineSimilarity(textVec, imageVec);
                                allResults.add(new SearchResultAdapter.SearchResult(photo, sim));
                            }

                            // 3. 유사도 순으로 정렬
                            Collections.sort(allResults, (a, b) -> Float.compare(b.similarity, a.similarity));

                            // 4. 유사도 0.25 이상인 모든 결과 필터링
                            float MINIMUM_SIMILARITY = 0.25f;
                            List<SearchResultAdapter.SearchResult> filteredResults = new ArrayList<>();
                            for (SearchResultAdapter.SearchResult result : allResults) {
                                if (result.similarity >= MINIMUM_SIMILARITY) {
                                    filteredResults.add(result);
                                }
                            }

                            // 5. UI 업데이트
                            activity.runOnUiThread(() -> {
                                if (!filteredResults.isEmpty()) {
                                    adapter.updateResults(filteredResults);
                                    resultCountTextView.setText(String.format("검색 결과: %d/%d (유사도 %.3f 이상)",
                                            filteredResults.size(), allResults.size(), MINIMUM_SIMILARITY));
                                    resultTextView.setText(String.format("유사도 %.3f 이상인 모든 이미지 (%d개)",
                                            MINIMUM_SIMILARITY, filteredResults.size()));

                                    // UI 요소 표시
                                    resultCountTextView.setVisibility(View.VISIBLE);
                                    resultGridRecyclerView.setVisibility(View.VISIBLE);
                                } else {
                                    resultTextView.setText(String.format("❌ 유사도 %.3f 이상인 이미지가 없습니다.", MINIMUM_SIMILARITY));
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            activity.runOnUiThread(() -> {
                                resultTextView.setText("❌ 오류 발생: " + e.getMessage());
                            });
                        }
                    }).start();
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

        // 8. Dialog 생성 (전체화면 테마 적용)
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.FullScreenDialogStyle);
        builder.setView(searchView);
        searchDialog = builder.create();

        // 블러 효과 및 레이아웃 설정
        if (searchDialog.getWindow() != null) {
            Window window = searchDialog.getWindow();

            // 배경 설정 - 반투명 밝은 회색
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#80DDDDDD")));

            // 전체 화면 레이아웃 설정
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            // 키보드가 올라올 때 레이아웃 조정 방식 설정
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

            // 상태바까지 확장
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            // 블러 효과 (Android 12 이상만 지원)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.setBackgroundBlurRadius(20); // 블러 강도 약간 줄임 25 -> 20
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
     * 현재 바텀 시트 상태 반환
     */
    public int getCurrentBottomSheetState() {
        return currentBottomSheetState;
    }

    /**
     * 토스트 메시지 표시
     */
    public void showToast(String message) {
        ToastManager.getInstance().showToast(message);
    }

    /**
     * 현재 날짜로 업데이트
     */
    public void updateToToday() {
        setDate(new Date());
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

    public void switchToStoryTab() {
        Log.d(TAG, "⭐⭐⭐ 스토리 탭으로 전환 시도");
        if (tabLayout != null && tabLayout.getTabCount() > 1) {
            TabLayout.Tab storyTab = tabLayout.getTabAt(1);
            if (storyTab != null) {
                Log.d(TAG, "⭐⭐⭐ 스토리 탭 선택");
                // 스토리 생성 완료 확인
                boolean hasStories = false;
                for (TimelineItem item : timelineItems) {
                    if (item.getStory() != null && !item.getStory().isEmpty()) {
                        hasStories = true;
                        break;
                    }
                }

                Log.d(TAG, "⭐⭐⭐ 스토리 존재 여부: " + (hasStories ? "있음" : "없음"));

                // UI 스레드에서 실행
                new Handler(Looper.getMainLooper()).post(() -> {
                    // 탭 선택
                    storyTab.select();

                    // 즉시 RecyclerView 설정 변경
                    if (storyRecyclerView != null && timelineRecyclerView != null) {
                        storyRecyclerView.setVisibility(View.VISIBLE);
                        timelineRecyclerView.setVisibility(View.GONE);
                        Log.d(TAG, "⭐⭐⭐ RecyclerView 가시성 설정: 스토리=표시, 타임라인=숨김");

                        // 어댑터 갱신 명시적 호출
                        if (storyAdapter != null) {
                            storyAdapter.notifyDataSetChanged();
                            Log.d(TAG, "⭐⭐⭐ 스토리 어댑터 명시적 갱신");
                        }
                    }
                });

                // 바텀 시트 상태 확인 및 조정
                if (currentBottomSheetState != BOTTOM_SHEET_EXPANDED) {
                    setBottomSheetState(BOTTOM_SHEET_EXPANDED);
                    Log.d(TAG, "⭐⭐⭐ 바텀 시트 확장됨");
                }
            }
        } else {
            Log.e(TAG, "❌ 탭 레이아웃이 없거나 탭이 부족함: " +
                    (tabLayout != null ? "탭 수=" + tabLayout.getTabCount() : "tabLayout=null"));
        }
    }
}