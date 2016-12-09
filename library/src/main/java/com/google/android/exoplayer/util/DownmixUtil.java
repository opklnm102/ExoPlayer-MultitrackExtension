package com.google.android.exoplayer.util;

import android.annotation.TargetApi;
import android.media.MediaCodec;

import java.nio.ByteBuffer;

@TargetApi(16)
public class DownmixUtil {

    public static ByteBuffer downmixBuffer(ByteBuffer[][] outputBuffers,
                                           MediaCodec.BufferInfo[] outputBufferInfo,
                                           int index, int trackCount) {

        byte[][] chunk = new byte[trackCount][];
        for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
            chunk[trackIndex] = new byte[outputBufferInfo[trackIndex].size];
            outputBuffers[trackIndex][index].get(chunk[trackIndex]);
            outputBuffers[trackIndex][index].clear();
        }

        byte[] downmixChunk = new byte[outputBufferInfo[0].size];
        for (int idx = 0; idx < downmixChunk.length; idx++) {
            for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
                downmixChunk[idx] += chunk[trackIndex][idx];
            }
            downmixChunk[idx] = (byte) (downmixChunk[idx] / Math.sqrt(trackCount));
        }
        return ByteBuffer.wrap(downmixChunk);
    }
}
