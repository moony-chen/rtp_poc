package com.example.rtp_poc;

//import com.bluejay.client.service.ContextContainer;
//import com.bluejay.client.util.NetworkUtils;

import android.util.Log;


import com.bluejay.rtp.RtpSession;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

public class AudioSender {
    private RtpSession session;


    private AudioSender(RtpSession session) {
        this.session = session;
    }

    public static AudioSender getInstance(RtpSession session) {
        AudioSender sender = new AudioSender(session);
        return sender;
    }

    public void init() {

    }

    public void send(LinkedList<byte[]> audio_q) {
        while (true) {
            //网络连通情况下才发送音频
//            if (NetworkUtils.isConnected(ContextContainer.getContext()) && CommandWord.isWakeuped()) {
            byte[] data = null;
            if (audio_q.size() > 0) {
                data = audio_q.poll();
//                Log.d("AudioSender", Arrays.toString(data));
//                try {
                    session.sendData(data, new Date().getTime());
//                    Thread.sleep(10000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

            }
//            }
        }
    }
}
