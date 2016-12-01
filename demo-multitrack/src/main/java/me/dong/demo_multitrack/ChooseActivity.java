package me.dong.demo_multitrack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class ChooseActivity extends AppCompatActivity {

    RecyclerView mRvVideo;
    VideoListAdapter mVideoListAdapter;
    public static final int WRITE_EXTERNAL_STORAGE_CODE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRvVideo = (RecyclerView) findViewById(R.id.rv_video);

        if (PermissionCheckUtil.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE_CODE)) {
            mVideoListAdapter = new VideoListAdapter(this, queryVideos());
            mRvVideo.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
            mRvVideo.setAdapter(mVideoListAdapter);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mVideoListAdapter = new VideoListAdapter(this, queryVideos());
                    mRvVideo.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
                    mRvVideo.setAdapter(mVideoListAdapter);
                } else {
                    this.finish();
                }
        }
    }

    private static final String[] VIDEO_PROJECTION =
            new String[]{
                    MediaStore.Video.VideoColumns._ID,
                    MediaStore.Video.VideoColumns.DATE_TAKEN,
                    MediaStore.Video.VideoColumns.DATE_MODIFIED,
                    MediaStore.Video.VideoColumns.MIME_TYPE,
                    MediaStore.Video.VideoColumns.DISPLAY_NAME
            };

    private List<MediaStoreData> queryVideos() {
        List<MediaStoreData> data = new ArrayList<>();

        Cursor cursor = getContentResolver()
                .query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        VIDEO_PROJECTION,
                        null, null, MediaStore.Video.VideoColumns.DATE_TAKEN + " DESC");

        if (cursor == null) {
            return data;
        }

        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_PROJECTION[0]));
                long dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_PROJECTION[1]));
                long dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(VIDEO_PROJECTION[2]));
                String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(VIDEO_PROJECTION[3]));
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(VIDEO_PROJECTION[4]));

                data.add(new MediaStoreData(id, Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Long.toString(id)),
                        dateTaken, dateModified, mimeType, displayName));
            }

        } finally {
            cursor.close();
        }
        return data;
    }


}
