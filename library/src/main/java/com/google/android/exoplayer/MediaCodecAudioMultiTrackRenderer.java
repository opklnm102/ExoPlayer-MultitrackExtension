package com.google.android.exoplayer;

import android.annotation.TargetApi;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.SystemClock;

import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.util.MimeTypes;

import java.nio.ByteBuffer;

/**
 * Created by Dong on 2016-11-30.
 */
@TargetApi(16)
public class MediaCodecAudioMultiTrackRenderer extends MediaCodecMultiTrackRenderer implements MediaClock {


    public interface EventListener extends MediaCodecMultiTrackRenderer.EventListener {

        /**
         * Invoked when an {@link AudioTrack} fails to initialize.
         *
         * @param e The corresponding exception.
         */
        void onAudioTrackInitializationError(AudioTrack.InitializationException e);

        /**
         * Invoked when an {@link AudioTrack} write fails.
         *
         * @param e The corresponding exception.
         */
        void onAudioTrackWriteError(AudioTrack.WriteException e);

        /**
         * Invoked when an {@link AudioTrack} underrun occurs.
         *
         * @param bufferSize             The size of the {@link AudioTrack}'s buffer, in bytes.
         * @param bufferSizeMs           The size of the {@link AudioTrack}'s buffer, in milliseconds, if it is
         *                               configured for PCM output. -1 if it is configured for passthrough output, as the buffered
         *                               media can have a variable bitrate so the duration may be unknown.
         * @param elapsedSinceLastFeedMs The time since the {@link AudioTrack} was last fed data.
         */
        void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
    }

    public static final int MSG_SET_VOLUME = 1;

    public static final int MSG_SET_PLAYBACK_PARAMS = 2;

    private final EventListener eventListener;
    private final AudioTrack audioTrack;

    private boolean passthroughEnabled;
    private android.media.MediaFormat passthroughMediaFormat;
    private int pcmEncoding;
    private int audioSessionId;
    private long currentPositionUs;
    private boolean allowPositionDiscontinuity;

    private boolean audioTrackHasData;
    private long lastFeedElapsedRealtimeMs;

    public MediaCodecAudioMultiTrackRenderer(SampleSource source, MediaCodecSelector mediaCodecSelector) {
        this(source, mediaCodecSelector, null, true);
    }

    public MediaCodecAudioMultiTrackRenderer(SampleSource source, MediaCodecSelector mediaCodecSelector,
                                             DrmSessionManager drmSessionManager, boolean playClearSamplesWithoutKeys) {
        this(source, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, null, null);
    }

    public MediaCodecAudioMultiTrackRenderer(SampleSource source, MediaCodecSelector mediaCodecSelector,
                                        Handler eventHandler, EventListener eventListener) {
        this(source, mediaCodecSelector, null, true, eventHandler, eventListener);
    }

    public MediaCodecAudioMultiTrackRenderer(SampleSource source, MediaCodecSelector mediaCodecSelector,
                                        DrmSessionManager drmSessionManager, boolean playClearSamplesWithoutKeys,
                                        Handler eventHandler, EventListener eventListener) {
        this(source, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
                eventListener, null, AudioManager.STREAM_MUSIC);
    }

    public MediaCodecAudioMultiTrackRenderer(SampleSource source, MediaCodecSelector mediaCodecSelector,
                                        DrmSessionManager drmSessionManager, boolean playClearSamplesWithoutKeys,
                                        Handler eventHandler, EventListener eventListener, AudioCapabilities audioCapabilities,
                                        int streamType) {
        this (new SampleSource[] {source}, mediaCodecSelector, drmSessionManager,
                playClearSamplesWithoutKeys, eventHandler, eventListener, audioCapabilities, streamType);
    }

    public MediaCodecAudioMultiTrackRenderer(SampleSource[] sources, MediaCodecSelector mediaCodecSelector,
                                        DrmSessionManager drmSessionManager, boolean playClearSamplesWithoutKeys,
                                        Handler eventHandler, EventListener eventListener, AudioCapabilities audioCapabilities,
                                        int streamType) {
        super(sources, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
                eventListener);
        this.eventListener = (EventListener) eventListener;
        this.audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
        this.audioTrack = new AudioTrack(audioCapabilities, streamType);
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec[] codec,
                                          ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo,
                                          int bufferIndex, boolean shouldSkip)
            throws ExoPlaybackException {

        if (passthroughEnabled && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {

            // modify
            for (int i = 0; i < codec.length; i++) {
                codec[i].releaseOutputBuffer(bufferIndex, false);
            }
            return true;
        }

        if (shouldSkip) {

            // add
            for (int i = 0; i < codec.length; i++) {
                codec[i].releaseOutputBuffer(bufferIndex, false);
                codecCounters.skippedOutputBufferCount++;
            }

//            codec.releaseOutputBuffer(bufferIndex, false);
//            codecCounters.skippedOutputBufferCount++;
            audioTrack.handleDiscontinuity();
            return true;
        }

        if (!audioTrack.isInitialized()) {
            // Initialize the AudioTrack now.
            try {
                if (audioSessionId != AudioTrack.SESSION_ID_NOT_SET) {
                    audioTrack.initialize(audioSessionId);
                } else {
                    audioSessionId = audioTrack.initialize();
                    onAudioSessionId(audioSessionId);
                }
                audioTrackHasData = false;
            } catch (AudioTrack.InitializationException e) {
                notifyAudioTrackInitializationError(e);
                throw new ExoPlaybackException(e);
            }
            if (getState() == TrackRenderer.STATE_STARTED) {
                audioTrack.play();
            }
        } else {
            // Check for AudioTrack underrun.
            boolean audioTrackHadData = audioTrackHasData;
            audioTrackHasData = audioTrack.hasPendingData();
            if (audioTrackHadData && !audioTrackHasData && getState() == TrackRenderer.STATE_STARTED) {
                long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs;
                long bufferSizeUs = audioTrack.getBufferSizeUs();
                long bufferSizeMs = bufferSizeUs == C.UNKNOWN_TIME_US ? -1 : bufferSizeUs / 1000;
                notifyAudioTrackUnderrun(audioTrack.getBufferSize(), bufferSizeMs, elapsedSinceLastFeedMs);
            }
        }

        int handleBufferResult;
        try {

            //Todo: downmix, buffer.
//            ByteBuffer downmixBuffer = downmixBuffer(buffer, bufferInfo);

            handleBufferResult = audioTrack.handleBuffer(
                    buffer, bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs);
            lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();
        } catch (AudioTrack.WriteException e) {
            notifyAudioTrackWriteError(e);
            throw new ExoPlaybackException(e);
        }

        // If we are out of sync, allow currentPositionUs to jump backwards.
        if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
            handleAudioTrackDiscontinuity();
            allowPositionDiscontinuity = true;
        }

        // Release the buffer if it was consumed.
        if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {

            // modify
            for (int i = 0; i < codec.length; i++) {
                codec[i].releaseOutputBuffer(bufferIndex, false);
                codecCounters.renderedOutputBufferCount++;
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean handlesTrack(MediaCodecSelector mediaCodecSelector, MediaFormat mediaFormat)
            throws MediaCodecUtil.DecoderQueryException {
        String mimeType = mediaFormat.mimeType;
        return MimeTypes.isAudio(mimeType) && (MimeTypes.AUDIO_UNKNOWN.equals(mimeType)
                || (allowPassthrough(mimeType) && mediaCodecSelector.getPassthroughDecoderInfo() != null)
                || mediaCodecSelector.getDecoderInfo(mimeType, false) != null);
    }

    @Override
    protected DecoderInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector, String mimeType,
                                         boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
        if (allowPassthrough(mimeType)) {
            DecoderInfo passthroughDecoderInfo = mediaCodecSelector.getPassthroughDecoderInfo();
            if (passthroughDecoderInfo != null) {
                passthroughEnabled = true;
                return passthroughDecoderInfo;
            }
        }
        passthroughEnabled = false;
        return super.getDecoderInfo(mediaCodecSelector, mimeType, requiresSecureDecoder);
    }

    /**
     * Returns whether encoded audio passthrough should be used for playing back the input format.
     * This implementation returns true if the {@link AudioTrack}'s audio capabilities indicate that
     * passthrough is supported.
     *
     * @param mimeType The type of input media.
     * @return True if passthrough playback should be used. False otherwise.
     */
    protected boolean allowPassthrough(String mimeType) {
        return audioTrack.isPassthroughSupported(mimeType);
    }

    @Override
    protected void configureCodec(MediaCodec codec, boolean codecIsAdaptive,
                                  android.media.MediaFormat format, android.media.MediaCrypto crypto) {
        String mimeType = format.getString(android.media.MediaFormat.KEY_MIME);
        if (passthroughEnabled) {
            // Override the MIME type used to configure the codec if we are using a passthrough decoder.
            format.setString(android.media.MediaFormat.KEY_MIME, MimeTypes.AUDIO_RAW);
            codec.configure(format, null, crypto, 0);
            format.setString(android.media.MediaFormat.KEY_MIME, mimeType);
            passthroughMediaFormat = format;
        } else {
            codec.configure(format, null, crypto, 0);
            passthroughMediaFormat = null;
        }
    }

    @Override
    protected MediaClock getMediaClock() {
        return this;
    }

    @Override
    protected void onInputFormatChanged(MediaFormatHolder holder, int idx) throws ExoPlaybackException {
        super.onInputFormatChanged(holder, idx);

        pcmEncoding = MimeTypes.AUDIO_RAW.equals(holder.format.mimeType) ? holder.format.pcmEncoding
                : C.ENCODING_PCM_16BIT;
    }

    @Override
    protected void onOutputFormatChanged(MediaCodec codec, android.media.MediaFormat outputFormat) {
        boolean passthrough = passthroughMediaFormat != null;
        String mimeType = passthrough
                ? passthroughMediaFormat.getString(android.media.MediaFormat.KEY_MIME)
                : MimeTypes.AUDIO_RAW;
        android.media.MediaFormat format = passthrough ? passthroughMediaFormat : outputFormat;
        int channelCount = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
        audioTrack.configure(mimeType, channelCount, sampleRate, pcmEncoding);
    }

    /**
     * Invoked when the audio session id becomes known. Once the id is known it will not change
     * (and hence this method will not be invoked again) unless the renderer is disabled and then
     * subsequently re-enabled.
     * <p>
     * The default implementation is a no-op. One reason for overriding this method would be to
     * instantiate and enable a {@link Virtualizer} in order to spatialize the audio channels. For
     * this use case, any {@link Virtualizer} instances should be released in {@link #onDisabled()}
     * (if not before).
     *
     * @param audioSessionId The audio session id.
     */
    protected void onAudioSessionId(int audioSessionId) {
        // Do nothing.
    }

    @Override
    protected void onStarted() {
        super.onStarted();
        audioTrack.play();
    }

    @Override
    protected void onStopped() {
        audioTrack.pause();
        super.onStopped();
    }

    @Override
    protected boolean isEnded() {
        return super.isEnded() && !audioTrack.hasPendingData();
    }

    @Override
    protected boolean isReady() {
        return audioTrack.hasPendingData() || super.isReady();
    }

    @Override
    public long getPositionUs() {
        long newCurrentPositionUs = audioTrack.getCurrentPositionUs(isEnded());
        if (newCurrentPositionUs != AudioTrack.CURRENT_POSITION_NOT_SET) {
            currentPositionUs = allowPositionDiscontinuity ? newCurrentPositionUs
                    : Math.max(currentPositionUs, newCurrentPositionUs);
            allowPositionDiscontinuity = false;
        }
        return currentPositionUs;
    }

    @Override
    protected void onDisabled() throws ExoPlaybackException {
        audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
        try {
            audioTrack.release();
        } finally {
            super.onDisabled();
        }
    }

    @Override
    protected void onDiscontinuity(long positionUs) throws ExoPlaybackException {
        super.onDiscontinuity(positionUs);
        audioTrack.reset();
        currentPositionUs = positionUs;
        allowPositionDiscontinuity = true;
    }

    @Override
    protected void onOutputStreamEnded() {
        audioTrack.handleEndOfStream();
    }

    protected void handleAudioTrackDiscontinuity() {
        // Do nothing
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        switch (messageType) {
            case MSG_SET_VOLUME:
                audioTrack.setVolume((Float) message);
                break;
            case MSG_SET_PLAYBACK_PARAMS:
                audioTrack.setPlaybackParams((PlaybackParams) message);
                break;
            default:
                super.handleMessage(messageType, message);
                break;
        }
    }

    private void notifyAudioTrackInitializationError(final AudioTrack.InitializationException e) {
        if (eventHandler != null && eventListener != null) {
            eventHandler.post(new Runnable() {
                @Override
                public void run() {
                    eventListener.onAudioTrackInitializationError(e);
                }
            });
        }
    }

    private void notifyAudioTrackWriteError(final AudioTrack.WriteException e) {
        if (eventHandler != null && eventListener != null) {
            eventHandler.post(new Runnable()  {
                @Override
                public void run() {
                    eventListener.onAudioTrackWriteError(e);
                }
            });
        }
    }

    private void notifyAudioTrackUnderrun(final int bufferSize, final long bufferSizeMs,
                                          final long elapsedSinceLastFeedMs) {
        if (eventHandler != null && eventListener != null) {
            eventHandler.post(new Runnable()  {
                @Override
                public void run() {
                    eventListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
                }
            });
        }
    }

}
