package com.example.wakey.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * 개별 마커와 클러스터 마커를 썸네일로 렌더링하는 클래스
 */
public class PhotoClusterRenderer extends DefaultClusterRenderer<PhotoClusterItem> {

    private static final String TAG = "MapCheck";

    public PhotoClusterRenderer(Context context, GoogleMap map, ClusterManager<PhotoClusterItem> clusterManager) {
        super(context, map, clusterManager);
    }

    @Override
    protected void onBeforeClusterItemRendered(PhotoClusterItem item, MarkerOptions markerOptions) {
        try {
            Bitmap thumbnail = item.getThumbnail();
            if (thumbnail != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(thumbnail, 150, 150, false);
                markerOptions
                        .icon(BitmapDescriptorFactory.fromBitmap(scaled))
                        .anchor(0.5f, 0.5f)
                        .title(null)
                        .snippet(null);
                Log.d(TAG, "📸 마커에 사진만 적용 (기본 마커 제거)");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 마커 렌더링 예외: " + e.getMessage());
        }
    }

    @Override
    protected void onBeforeClusterRendered(Cluster<PhotoClusterItem> cluster, MarkerOptions markerOptions) {
        try {
            Bitmap thumbnail = getClusterThumbnail(cluster);
            if (thumbnail != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(thumbnail, 160, 160, false);
                markerOptions
                        .icon(BitmapDescriptorFactory.fromBitmap(scaled))
                        .anchor(0.5f, 0.5f)
                        .title(null)
                        .snippet(null);
                Log.d(TAG, "📸 클러스터에 대표 사진 적용");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 클러스터 렌더링 예외: " + e.getMessage());
        }
    }

    private Bitmap getClusterThumbnail(Cluster<PhotoClusterItem> cluster) {
        for (PhotoClusterItem item : cluster.getItems()) {
            if (item.getThumbnail() != null) {
                return item.getThumbnail();
            }
        }
        return null;
    }

    @Override
    protected boolean shouldRenderAsCluster(Cluster<PhotoClusterItem> cluster) {
        // 무조건 false 반환시 개별 마커만 보임. 대신 클러스터 렌더링 조건으로 조절 가능
        return cluster.getSize() > 1;
    }
}
