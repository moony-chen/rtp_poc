package com.example.rtp_poc;

import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

public class AudioPlayer {
    private static final String TAG = "AudioPlayer";
    //指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
    private int mSampleRate=16000 ;
    //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
    private int mChannelConfig= AudioFormat.CHANNEL_OUT_MONO; //单声道
    //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
    //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
    private int mAudioFormat=AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize = 0;
    private AudioTrack audioTrack;
    private boolean isStop = false;

    private AudioPlayer(){
    }
    public static AudioPlayer getInstance(){
        AudioPlayer player = new AudioPlayer();
        return player;
    }
    public void init(){
        bufferSize = AudioTrack.getMinBufferSize(mSampleRate, mChannelConfig, mAudioFormat);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, mChannelConfig,mAudioFormat, bufferSize, AudioTrack.MODE_STREAM);
    }
    public void play(InputStream stream){
        try {
            Log.v(TAG, "start play");
            audioTrack.play();
            isStop = false;
            byte[] buffer = new byte[bufferSize];
            int len = 0;
            while ((len = stream.read(buffer)) != -1 && !isStop) {
                try {
                    Log.d(TAG, "play audio: len " + len);
                    audioTrack.write(buffer, 0, len);
                } catch (Exception e) {
                    Log.e(TAG, "play audio: e : " + e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "play audio: e : " + e);
        }
        finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.e(TAG, "close stream: e : " + e);
                }
            }
        }
    }


    public void play(byte[] buffer){
        if(buffer!=null && buffer.length>0) {
            InputStream stream = new ByteArrayInputStream(buffer);
            play(stream);
        }
    }
    public void play(LinkedList<byte[]> audio_q){
        try {
            Log.v(TAG, "start play");
            audioTrack.play();
            isStop = false;
            byte[] buffer = new byte[bufferSize];
            int len = 0;
            while (!isStop) {
                try {
                    if(audio_q!=null && audio_q.size()>0) {
                        buffer = audio_q.poll();
                        if(buffer !=null && buffer.length>0) {
                            len = buffer.length;
                            Log.d(TAG, "play audio: len " + len);
                            audioTrack.write(buffer, 0, len);
                        }
                    }
                }
                catch (Exception e){
                    Log.e(TAG, "play audio: e : " + e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "play audio: e : " + e);
        }
    }
    public void stop(){
        Log.v(TAG, "stop play");
        isStop = true;
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
    }
    public void setmSampleRate(int mSampleRate){
        this.mSampleRate = mSampleRate;
    }

    public void setmChannelConfig(int mChannelConfig){
        this.mChannelConfig = mChannelConfig;
    }
}
