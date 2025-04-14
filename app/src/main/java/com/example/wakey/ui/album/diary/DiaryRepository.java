package com.example.wakey.ui.album.diary;

import android.content.Context;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiaryRepository {
    private static DiaryRepository instance;
    private final Context context;
    private final List<DiaryItem> diaryItems = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private DiaryRepository(Context context) {
        this.context = context.getApplicationContext();
        loadSampleDiaries();
    }

    public static synchronized DiaryRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DiaryRepository(context);
        }
        return instance;
    }

    public List<DiaryItem> getDiaries() {
        return diaryItems;
    }

    public String getDiaryContent(String title) {
        if (title.contains("치앙마이")) {
            return "치앙마이에서의 특별한 7일간의 여행. 처음으로 친구와 단둘이가는 여행인만큼 잊지 못할 추억이 될 것 같다. 3개월동안 힘들었던 학기를 마치고 힐링을 하고 왔다. 고2때부터 노래를 부르던 치앙마이. 드디어 다녀왔다. 상상 그 이상으로 좋았다. 반드시 또 갈거다. 돈 많이 벌어야겠다...";
        } else if (title.contains("부산")) {
            return "해외여행은 이렇게나 많이 가봤는데 부산은 또 처음이라,,, 근데 그게 좋은 사람들과 함께한 시간이라 더 좋았다. 술도 많이 마시고, 바다도 많이 구경하고, 이야기도 많이.... 나눴나...? 일단 술은 확실히 많이 마셨다. 2박 3일동안 정말 알차게 즐기고 온 여행이었다.";
        } else if (title.contains("일본")) {
            return "돌아보니 너무 소중했던 일본 소도시 여행. 지금은 물가도 많이 오르고 엔화도 많이 올라서 일본 갈 엄두가 나지 않지만, 저때는 정말 모든걸 즐기고 온 것 같다. 항상 느끼지만 정말 잘 다녀온 일본 여행이었다. 나름 다사다난하고 덥기도 꽤나 더웠지만, 다시 저때로 돌아가고 싶다.";
        } else {
            return "진짜 여행은 가도가도 끝이 없는 것 같다. 영원히 여행만 다니며 살고싶다. 하지만, 일상이라는 삶이 존재하기에 나는 하루하루 여행 갈 날만을 손꼽아 기다리며 일상을 버틴다.";
        }
    }

    public void addDiary(DiaryItem diaryItem) {
        diaryItems.add(0, diaryItem); // 최신 항목이 맨 위에 오도록
    }

    private void loadSampleDiaries() {
        // 샘플 일기 데이터 추가
        String chiangmaiThumbUri = "android.resource://" + context.getPackageName() + "/" + R.drawable.thailand_sample;
        diaryItems.add(new DiaryItem(
                "치앙마이에서의 일기",
                "2024.07.16 ~ 07.23",
                chiangmaiThumbUri,
                5
        ));

        // 제주도 일기 썸네일
        String busanThumbUri = "android.resource://" + context.getPackageName() + "/" + R.drawable.busan_sample;
        diaryItems.add(new DiaryItem(
                "친구들과 부산 여행",
                "2025.02.15 ~ 02.17",
                busanThumbUri,
                4
        ));

        // 도쿄 일기 썸네일
        String japanThumbUri = "android.resource://" + context.getPackageName() + "/" + R.drawable.japan_sample;
        diaryItems.add(new DiaryItem(
                "일본 소도시 여행",
                "2023.06.21 ~ 07.01",
                japanThumbUri,
                5
        ));

        // 기존 사진 DB에서 추가 샘플 데이터 가져오기
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            List<Photo> allPhotos = db.photoDao().getAllPhotos();

            List<DiaryItem> additionalDiaries = new ArrayList<>();

            if (allPhotos.size() > 10) {
                additionalDiaries.add(new DiaryItem(
                        "나의 일상",
                        "2025.02.19 ~ 02.20",
                        allPhotos.get(0).filePath,
                        3
                ));
            }

            diaryItems.addAll(additionalDiaries);
        });
    }
}