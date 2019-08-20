package com.example.rtp_poc;

import android.content.Context;
import android.util.Log;


import com.bluejay.rtp.DataPacket;
import com.bluejay.rtp.RtpParticipant;
import com.bluejay.rtp.RtpSession;
import com.bluejay.rtp.RtpSessionDataListener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;


public class AudioReceiver implements RtpSessionDataListener {

    private final String TAG = "AudioReceiver";

    private Context context;

    private ConcurrentLinkedQueue<byte[]> stream = new ConcurrentLinkedQueue<>();
    FileOutputStream outputStream;

    public static AudioReceiver getInstance(Context context) {
        AudioReceiver receiver = new AudioReceiver(context);
        return receiver;
    }

    public void init() {

    }

    public AudioReceiver (Context context) {
        String filename = "myfile.wav";
        this.context = context;


        try {
            outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);

//            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ConcurrentLinkedQueue<byte[]> receive() {
        return this.stream;
    }

    @Override
    public void dataPacketReceived(RtpSession session, RtpParticipant participant, DataPacket packet) {
        Log.d(TAG,  packet.toString());
        byte[] data = packet.getDataAsArray();
        stream.offer(data);

        try {
            outputStream.write(data);

        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("AudioReceiver", String.format("AudioReceiver steam length %d", stream.size() ));
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
