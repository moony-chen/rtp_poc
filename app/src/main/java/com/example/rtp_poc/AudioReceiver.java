package com.example.rtp_poc;

import android.content.Context;
import android.util.Log;

import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;

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
    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
        Log.d("AudioReceiver", Arrays.toString(packet.getDataAsArray()));
        stream.offer(packet.getDataAsArray());
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
