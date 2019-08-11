package com.example.rtp_poc;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;


public class AudioReceiver {
    private Context context;

    public static AudioReceiver getInstance() {
        AudioReceiver receiver = new AudioReceiver();
        return receiver;
    }

    public void init() {

    }

    public void receive(byte[] data) {
        Log.d("AudioReceiver", Arrays.toString(data));

    }

    public void setContext(Context context) {
        this.context = context;
    }
}
