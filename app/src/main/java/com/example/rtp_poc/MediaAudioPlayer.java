package com.example.rtp_poc;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;

import java.util.Queue;

public class MediaAudioPlayer implements IAudioPlayer {

    private ExoPlayer player;

    public MediaAudioPlayer(Context context) {

        DefaultRenderersFactory renderer = new DefaultRenderersFactory(context);
        renderer.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);

        player = ExoPlayerFactory.newSimpleInstance(context, renderer, new DefaultTrackSelector());

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
