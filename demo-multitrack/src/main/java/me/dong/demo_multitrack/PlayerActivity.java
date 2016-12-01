package me.dong.demo_multitrack;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.util.Util;

import me.dong.demo_multitrack.player.DemoPlayer;
import me.dong.demo_multitrack.player.ExtractorRendererBuilder;

public class PlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback,
        DemoPlayer.Listener {

    public static final String TAG = PlayerActivity.class.getSimpleName();

    private AspectRatioFrameLayout mVideoFrame;
    private SurfaceView mSurfaceView;
    private MediaController mMediaController;
    private View root;
    private View mShutterView;

    private DemoPlayer mDemoPlayer;
    private boolean mPlayerNeedsPrepare;
    private long mPlayerPosition;

    private Uri mContentUri;
    private int mContentType;
    private String mDisplayName;
    private String mMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVideoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(this);
        mShutterView = findViewById(R.id.shutter);
        root = findViewById(R.id.root);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    toggleControllsVisibility();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            }
        });

        mMediaController = new KeyCompatibleMediaController(this);
        mMediaController.setAnchorView(root);
    }

    private void toggleControllsVisibility() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            showControls();
        }
    }

    private void showControls() {
        mMediaController.show(0);
    }

    @Override
    public void onNewIntent(Intent intent) {
        releasePalyer();
        mPlayerPosition = 0;
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            onShown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || mDemoPlayer == null) {
            onShown();
        }
    }

    private void onShown() {
        Intent receiveIntent = getIntent();

        mContentUri = receiveIntent.getParcelableExtra("uri");
        mDisplayName = receiveIntent.getStringExtra("displayName");
        mMimeType = receiveIntent.getStringExtra("mimeType");
        mContentType = Util.TYPE_OTHER;
        Log.e(TAG, mContentUri + " " + mDisplayName + " " + mMimeType);

        if (mDemoPlayer == null) {
            preparePlayer(true);
        } else {
            mDemoPlayer.setBackgrounded(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            onHidden();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            onHidden();
        }
    }

    private void onHidden() {
        releasePalyer();
        mShutterView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePalyer();
    }

    private void releasePalyer() {
        if (mDemoPlayer != null) {
            mPlayerPosition = mDemoPlayer.getCurrentPosition();
            mDemoPlayer.release();
            mDemoPlayer = null;
        }
    }

    private void preparePlayer(boolean playWhenReady) {
        if (mDemoPlayer == null) {
            mDemoPlayer = new DemoPlayer(getRendererBuilder());
            mDemoPlayer.addListener(this);
//            player.setCaptionListener(this);
//            player.setMetadataListener(this);
            mDemoPlayer.seekTo(mPlayerPosition);
            mPlayerNeedsPrepare = true;

            mMediaController.setMediaPlayer(mDemoPlayer.getPlayerControl());
            mMediaController.setEnabled(true);

        }

        if (mPlayerNeedsPrepare) {
            mDemoPlayer.prepare();
            mPlayerNeedsPrepare = false;
        }
        mDemoPlayer.setSurface(mSurfaceView.getHolder().getSurface());
        mDemoPlayer.setPlayWhenReady(playWhenReady);
    }

    private DemoPlayer.RendererBuilder getRendererBuilder() {
        String userAgent = Util.getUserAgent(this, "ExoPlayerDemo");

        switch (mContentType) {
            case Util.TYPE_OTHER:
                return new ExtractorRendererBuilder(this, userAgent, mContentUri);
            default:
                throw new IllegalStateException("Unsupported type: " + mContentType);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (mDemoPlayer != null) {
            mDemoPlayer.setSurface(surfaceHolder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (mDemoPlayer != null) {
            mDemoPlayer.blockingClearSurface();
        }

    }

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
    }

    @Override
    public void onError(Exception e) {
        mPlayerNeedsPrepare = true;
        showControls();
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        mShutterView.setVisibility(View.GONE);
        mVideoFrame.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
    }

    private static final class KeyCompatibleMediaController extends MediaController {

        private MediaController.MediaPlayerControl mMediaPlayerControl;

        public KeyCompatibleMediaController(Context context) {
            super(context);
        }

        @Override
        public void setMediaPlayer(MediaPlayerControl playerControl) {
            super.setMediaPlayer(playerControl);
            this.mMediaPlayerControl = playerControl;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            int keyCode = event.getKeyCode();
            if (mMediaPlayerControl.canSeekForward() && (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    mMediaPlayerControl.seekTo(mMediaPlayerControl.getCurrentPosition() + 15000);
                    show();
                }
                return true;
            } else if (mMediaPlayerControl.canSeekBackward() && (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    mMediaPlayerControl.seekTo(mMediaPlayerControl.getCurrentPosition() - 5000);
                    show();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }
}
