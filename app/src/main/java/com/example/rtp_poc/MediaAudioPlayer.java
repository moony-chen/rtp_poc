package com.example.rtp_poc;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

import java.util.Queue;

public class MediaAudioPlayer implements IAudioPlayer {

    private ExoPlayer player;

    public MediaAudioPlayer(Context context) {

        DefaultRenderersFactory renderer = new DefaultRenderersFactory(context);
        renderer.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);

        LoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        500, //DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        1000, // DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        0, // DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        0//DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                ).createDefaultLoadControl();


        player = ExoPlayerFactory.newSimpleInstance(context, renderer, new DefaultTrackSelector(), lc);

    }

    @Override
    public void play(Queue<byte[]> audio_q) {

        ByteArrayQueueDataSource byteArrayDataSource = new ByteArrayQueueDataSource(audio_q);
//            ByteArrayDataSource byteArrayDataSource = new ByteArrayDataSource(fileData2);
        DataSource.Factory factory = new DataSource.Factory() {
            @Override
            public DataSource createDataSource() {
                return byteArrayDataSource;
            }
        };
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(Uri.EMPTY);

        player.prepare(mediaSource);
        player.setPlayWhenReady(true);

    }
}
