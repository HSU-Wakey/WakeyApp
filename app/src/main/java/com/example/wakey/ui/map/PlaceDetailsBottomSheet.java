package com.example.wakey.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import java.util.Arrays;
import java.util.List;

public class PlaceDetailsBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PLACE_ID = "place_id";

    private PlacesClient placesClient;
    private String placeId;

    // UI components
    private TextView nameTextView;
    private TextView addressTextView;
    private RatingBar ratingBar;
    private TextView ratingTextView;
    private ImageView placeImageView;
    private RecyclerView reviewsRecyclerView;
    private View loadingView;
    private TextView phoneTextView;
    private TextView websiteTextView;
    private TextView hoursTextView;

    public static PlaceDetailsBottomSheet newInstance(String placeId) {
        PlaceDetailsBottomSheet fragment = new PlaceDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PLACE_ID, placeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            placeId = getArguments().getString(ARG_PLACE_ID);
        }

        // Places API 클라이언트 초기화
        placesClient = com.google.android.libraries.places.api.Places.createClient(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_place_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI 컴포넌트 초기화
        nameTextView = view.findViewById(R.id.place_name);
        addressTextView = view.findViewById(R.id.place_address);
        ratingBar = view.findViewById(R.id.place_rating_bar);
        ratingTextView = view.findViewById(R.id.place_rating_text);
        placeImageView = view.findViewById(R.id.place_image);
        reviewsRecyclerView = view.findViewById(R.id.reviews_recycler_view);
        loadingView = view.findViewById(R.id.loading_view);
        phoneTextView = view.findViewById(R.id.place_phone);
        websiteTextView = view.findViewById(R.id.place_website);
        hoursTextView = view.findViewById(R.id.place_hours);

        // 리사이클러뷰 설정
        reviewsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 장소 정보 로드
        loadPlaceDetails();
    }

    private void loadPlaceDetails() {
        // 로딩 표시
        loadingView.setVisibility(View.VISIBLE);

        // 요청할 필드 목록
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.PHONE_NUMBER,
                Place.Field.WEBSITE_URI,
                Place.Field.OPENING_HOURS,
                Place.Field.PHOTO_METADATAS
        );

        // 장소 상세 정보 요청
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

        placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
            Place place = response.getPlace();

            // UI 업데이트
            nameTextView.setText(place.getName());
            addressTextView.setText(place.getAddress());

            if (place.getRating() != null) {
                float rating = place.getRating().floatValue();
                ratingBar.setRating(rating);
                ratingTextView.setText(String.format("%.1f/5 (%d)",
                        rating, place.getUserRatingsTotal() != null ? place.getUserRatingsTotal() : 0));
            } else {
                ratingBar.setVisibility(View.GONE);
                ratingTextView.setText("평점 없음");
            }

            // 전화번호 설정
            if (place.getPhoneNumber() != null) {
                phoneTextView.setText(place.getPhoneNumber());
                phoneTextView.setVisibility(View.VISIBLE);
            } else {
                phoneTextView.setVisibility(View.GONE);
            }

            // 웹사이트 설정
            if (place.getWebsiteUri() != null) {
                websiteTextView.setText(place.getWebsiteUri().toString());
                websiteTextView.setVisibility(View.VISIBLE);
            } else {
                websiteTextView.setVisibility(View.GONE);
            }

            // 영업시간 설정
            if (place.getOpeningHours() != null) {
                StringBuilder hoursText = new StringBuilder();
                for (String period : place.getOpeningHours().getWeekdayText()) {
                    hoursText.append(period).append("\n");
                }
                hoursTextView.setText(hoursText.toString());
                hoursTextView.setVisibility(View.VISIBLE);
            } else {
                hoursTextView.setVisibility(View.GONE);
            }

            // 이미지 로드
            List<PhotoMetadata> photoMetadatas = place.getPhotoMetadatas();
            if (photoMetadatas != null && !photoMetadatas.isEmpty()) {
                PhotoMetadata photoMetadata = photoMetadatas.get(0);
                FetchPhotoRequest photoRequest = FetchPhotoRequest.builder(photoMetadata)
                        .setMaxWidth(500)
                        .setMaxHeight(300)
                        .build();

                placesClient.fetchPhoto(photoRequest).addOnSuccessListener(fetchPhotoResponse -> {
                    placeImageView.setVisibility(View.VISIBLE);
                    Glide.with(requireContext())
                            .load(fetchPhotoResponse.getBitmap())
                            .into(placeImageView);
                }).addOnFailureListener(exception -> {
                    placeImageView.setVisibility(View.GONE);
                });
            } else {
                placeImageView.setVisibility(View.GONE);
            }

            // 로딩 표시 숨김
            loadingView.setVisibility(View.GONE);
        }).addOnFailureListener((exception) -> {
            // 오류 처리
            loadingView.setVisibility(View.GONE);
            // 오류 메시지 표시 로직 추가
        });
    }
}