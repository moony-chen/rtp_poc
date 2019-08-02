package com.example.rtp_poc;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpDatasource;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.biasedbit.efflux.session.SingleParticipantSession;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Enumeration;


import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import io.reactivex.Observable;
import io.reactivex.Observer;
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


    }

//    private  SingleParticipantSession session;

    private void startAudio() {
        // AudioRecord 得到录制最小缓冲区的大小
        bufferSize = AudioRecord.getMinBufferSize(mSampleRate,
                mChannelConfig,
                mAudioFormat);
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
}
