package com.example.rtp_poc;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.StrictMode;
import android.util.Log;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class AudioSource {

    private static final String TAG = "AudioSource";

    //指定音频源 这个和MediaRecorder是相同的 MediaRecorder.AudioSource.MIC指的是麦克风
    private final int mAudioSource = MediaRecorder.AudioSource.MIC;
    //指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
    private int mSampleRate=16000 ;
    //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
    private int mChannelConfig= AudioFormat.CHANNEL_IN_MONO; //单声道
    //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
    //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
    private int mAudioFormat=AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize = 0;
    private AudioRecord audioRecord;

    private PublishSubject<byte[]> audioSource = PublishSubject.create();

    private Context context;

    public AudioSource(Context context) {
        this.context = context;

    }

    public Observable<byte[]> getAudioSource() {
        return this.audioSource;
    }



    public void startAudio() {
        // AudioRecord 得到录制最小缓冲区的大小
        bufferSize = AudioRecord.getMinBufferSize(mSampleRate,
                mChannelConfig,
                mAudioFormat);
        Log.d(TAG, "bufferSize" + bufferSize);
//        bufferSize = 960;
        // 实例化播放音频对象
        audioRecord = new AudioRecord(mAudioSource, mSampleRate,
                mChannelConfig,
                mAudioFormat, bufferSize);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        AudioManager audio =  (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);

        try {
            audioRecord.startRecording();
            new Thread(new Runnable() {
                @Override
                public void run() {

                    byte[] buffer = new byte[bufferSize];
                    while (audioRecord.read(buffer, 0, bufferSize) > 0) {
                        AudioSource.this.audioSource.onNext(buffer);
                    }
                }
            }, "AudioRecorder Thread").start();


        } catch (Exception e) {
            Log.e("----------------------", e.toString());
            e.printStackTrace();
        }
    }

    public void stopAudio() {
        audioRecord.stop();
        audioRecord.release();
    }

}