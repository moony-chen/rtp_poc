package com.example.rtp_poc;

import android.content.Context;
import android.util.Log;


import com.bluejay.rtp.DataPacket;
import com.bluejay.rtp.RtpParticipant;
import com.bluejay.rtp.RtpSession;
import com.bluejay.rtp.RtpSessionDataListener;

import java.util.Arrays;
import java.util.LinkedList;


public class AudioReceiver implements RtpSessionDataListener {
    private Context context;

    private LinkedList<byte[]> stream = new LinkedList<>();

    public static AudioReceiver getInstance() {
        AudioReceiver receiver = new AudioReceiver();
        return receiver;
    }

    public void init() {

    }

    public LinkedList<byte[]> receive() {
        return this.stream;
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipant participant, DataPacket packet) {
        byte[] data = packet.getDataAsArray();
//        Log.d("AudioReceiver", Arrays.toString(data));
        stream.offer(data);
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
