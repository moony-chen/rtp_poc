package com.example.rtp_poc;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
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

public class MainActivity extends AppCompatActivity {

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
                    send();

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

//    private  SingleParticipantSession session;

    private void send() {
        String remoteAddress = ((EditText)findViewById(R.id.editText2)).getText().toString();
        String remotePort = ((EditText)findViewById(R.id.editText1)).getText().toString();
        new LongOperation().execute(remoteAddress, remotePort);





//        while ((offset += audioRecord.read(buffer, offset, bufferSize)) > 0) {
//
//            dp.setSequenceNumber(seq++);
//            dp.setData(buffer);
//            session.sendDataPacket(dp);
//
//        }

    }

    private class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
//            super.channelActive(ctx);
            ctx.writeAndFlush(Unpooled.copiedBuffer("test", CharsetUtil.UTF_8));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            Log.d(TAG, msg.toString(CharsetUtil.UTF_8));
        }
    }

    private class LongOperation extends AsyncTask<String, Void, String> {

        DatagramSocket socket;
//        private final ChannelGroup group = new DefaultChannelGroup();

        @Override
        protected String doInBackground(String... params)  {
//            RtpParticipant localP = RtpParticipant.createReceiver("127.0.0.1", 11111, 11112);
//            RtpParticipant remoteP = RtpParticipant.createReceiver("10.0.2.3", 50559 , 21112);

//            session = new SingleParticipantSession("id", 1, localP, remoteP);
//            session.addDataListener(new RtpSessionDataListener() {
//                @Override
//                public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
//                    Logger.getLogger(MainActivity.class).debug(packet.getDataAsArray().toString());
//                }
//            });
//            session.init();
            EventLoopGroup group = new NioEventLoopGroup();

            try {
                 socket = new DatagramSocket();
                final InetAddress destination = InetAddress.getByName("10.0.2.3");
                byte[] buffer = new byte[]{(byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5};
//                DatagramPacket packet =  new DatagramPacket (buffer,buffer.length,destination,50559);
//                socket.send(packet);

                SocketAddress remote = new InetSocketAddress("10.0.2.3", 50559);


                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioDatagramChannel.class)
                        .remoteAddress(remote)
                        .handler(new ChannelInitializer<DatagramChannel>() {

                            @Override
                            protected void initChannel(DatagramChannel ch) throws Exception {
                                ch.pipeline().addLast(new ClientHandler());
                            }
                        });
                ChannelFuture f = b.connect().sync();
                f.channel().closeFuture().sync();


            } catch (Exception e) {
                e.printStackTrace();
            }

//        audioRecord.startRecording();
//            byte[] buffer = new byte[bufferSize];
//            DataPacket dp = new DataPacket();
//            dp.setPayloadType(1);
//            int seq = 1;
//            int offset = 0;
//
//            dp.setSequenceNumber(seq++);
//            dp.setData();
//            session.sendDataPacket(dp);
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            socket.close();
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
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
}
