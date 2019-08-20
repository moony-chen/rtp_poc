package com.example.rtp_poc;

import java.util.Queue;

public interface IAudioPlayer {

    void play(Queue<byte[]> audio_q);
}
