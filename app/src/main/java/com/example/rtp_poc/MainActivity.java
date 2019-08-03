package com.example.rtp_poc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.biasedbit.efflux.session.SingleParticipantSession;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 100;

    private static final String TAG = "MainActivity";
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
    private CompositeDisposable cd = new CompositeDisposable();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                return;
        }

        startAudio();



        Observable<Integer> volume$ = this.audioSource.subscribeOn(Schedulers.computation())
                .map(new Function<byte[], Integer>() {
                    @Override
                    public Integer apply(byte[] bytes) throws Exception {
                        return calculateVolume(bytes, 16);
                    }
                });

        cd.add(
                volume$
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        TextView volumeView = findViewById(R.id.volumeView);
                        volumeView.setText(String.format(Locale.US, "Volume: %d", integer));
                    }
                })
        );

        cd.add(
                volume$
                .map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer) throws Exception {
                        return integer > 0 ? 1 : 0;
                    }
                })
                        .scan(1, new BiFunction<Integer, Integer, Integer>() {
                            @Override
                            public Integer apply(Integer acc, Integer value) throws Exception {
                                if (value == 1) return 0;
                                else return acc + 1;
                            }
                        })
                        .scan(false, new BiFunction<Boolean, Integer, Boolean>() {
                            @Override
                            public Boolean apply(Boolean aBoolean, Integer integer) throws Exception {
                                if (integer == 0) return true;
                                else return  (aBoolean && integer < 32);
                                // 32: threshold, if lasts about 2 seconds of no talk, consider user no longer speaks
                            }
                        })

                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean talking) throws Exception {
                        TextView volumeView = findViewById(R.id.talkingView);
                        volumeView.setText(String.format(Locale.US, "Talking: %s", talking? "Yes" : "No"));
                    }
                })
        );


    }

//    private  SingleParticipantSession session;

    private void startAudio() {
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
        try {
            AudioManager audio =  (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);




//            InetAddress ia = InetAddress.getByAddress(getLocalIPAddress());

//            ((TextView)findViewById(R.id.lblLocalPort)).setText(String.valueOf(localPort));
            findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    initRtc(MainActivity.this.audioSource);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            send();
                        }
                    }, "AudioRecorder Thread").start();


                }
            });

            ((Button) findViewById(R.id.button2)).setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    stop();
                }
            });

        } catch (Exception e) {
            Log.e("----------------------", e.toString());
            e.printStackTrace();
        }
    }

    private PublishSubject<byte[]> audioSource = PublishSubject.create();

    private void initRtc(final Observable<byte[]> audioSource) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String remoteAddress = ((EditText)findViewById(R.id.editText2)).getText().toString();
                String remotePort = ((EditText)findViewById(R.id.editText1)).getText().toString();

                RtpParticipant localP = RtpParticipant.createReceiver("127.0.0.1", 12345, 11113);
                RtpParticipant remoteP = RtpParticipant.createReceiver(remoteAddress, Integer.parseInt(remotePort) , 21112);

                RtpSession session = new SingleParticipantSession("id", 1, localP, remoteP);
                session.addDataListener(new RtpSessionDataListener() {
                    @Override
                    public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
                        Logger.getLogger(MainActivity.class).debug(packet.getDataAsArray().toString());
                    }
                });
                try {
                    session.init(audioSource);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "RTC thread").start();


    }


    private void send() {


//        this.audioSource.onNext(new byte[]{(byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5});
        audioRecord.startRecording();
        byte[] buffer = new byte[bufferSize];


        while (audioRecord.read(buffer, 0, bufferSize) > 0) {

            this.audioSource.onNext(buffer);

        }
//
//        mStreamAudioRecorder.start(new StreamAudioRecorder.AudioDataCallback() {
//            @Override
//            public void onAudioData(byte[] data, int size) {
//                if (mFileOutputStream != null) {
//                    try {
//                        mFileOutputStream.write(data, 0, size);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//
//            @Override
//            public void onError() {
//                mBtnStart.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        Toast.makeText(getApplicationContext(), "Record fail",
//                                Toast.LENGTH_SHORT).show();
//                        mBtnStart.setText("Start");
//                        mIsRecording = false;
//                    }
//                });
//            }
//        });

    }

    public int getValidSampleRates() {
        for (int rate : new int[] {16000, 8000}) {  // add the rates you wish to check against
            int bufferSize = AudioRecord.getMinBufferSize(rate, mChannelConfig, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                return bufferSize;

            }
        }
        return 0;
    }

    private void stop() {
        audioRecord.stop();
//        audioRecord.release();
//        session.terminate();
    }

    private byte[] getLocalIPAddress() {
        byte[] bytes = null;

        try {
            // get the string ip
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

            // convert to bytes
            InetAddress inetAddress = null;
            try {
                inetAddress = InetAddress.getByName(ip);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            bytes = new byte[0];
            if (inetAddress != null) {
                bytes = inetAddress.getAddress();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "phone_voip_incompatible", Toast.LENGTH_SHORT).show();
        }

        return bytes;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    startAudio();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    /**
     * 静音检测
     * @param buffer
     * @param audioFormat
     * @return
     */
    private static int calculateVolume(byte[] buffer, int audioFormat) {
        int[] var3 = null;
        int var4 = buffer.length;
        int var2;
        if(audioFormat == 8) {
            var3 = new int[var4];
            for(var2 = 0; var2 < var4; ++var2) {
                var3[var2] = buffer[var2];
            }
        } else if(audioFormat == 16) {
            var3 = new int[var4 / 2];
            for(var2 = 0; var2 < var4 / 2; ++var2) {
                byte var5 = buffer[var2 * 2];
                byte var6 = buffer[var2 * 2 + 1];
                int var13;
                if(var5 < 0) {
                    var13 = var5 + 256;
                } else {
                    var13 = var5;
                }
                short var7 = (short)(var13 + 0);
                if(var6 < 0) {
                    var13 = var6 + 256;
                } else {
                    var13 = var6;
                }
                var3[var2] = (short)(var7 + (var13 << 8));
            }
        }

        int[] var8 = var3;
        if(var3 != null && var3.length != 0) {
            float var10 = 0.0F;
            for(int var11 = 0; var11 < var8.length; ++var11) {
                var10 += (float)(var8[var11] * var8[var11]);
            }
            var10 /= (float)var8.length;
            float var12 = 0.0F;
            for(var4 = 0; var4 < var8.length; ++var4) {
                var12 += (float)var8[var4];
            }
            var12 /= (float)var8.length;
            var4 = (int)(Math.pow(2.0D, (double)(audioFormat - 1)) - 1.0D);
            double var14 = Math.sqrt((double)(var10 - var12 * var12));
            int var9;
            if((var9 = (int)(10.0D * Math.log10(var14 * 10.0D * Math.sqrt(2.0D) / (double)var4 + 1.0D))) < 0) {
                var9 = 0;
            }
            if(var9 > 10) {
                var9 = 10;
            }
            return var9;
        } else {
            return 0;
        }
    }

    @Override
    protected void onDestroy() {
        cd.dispose();
        super.onDestroy();
    }
}
