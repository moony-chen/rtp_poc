package com.example.rtp_poc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.bluejay.rtp.DataPacket;
import com.bluejay.rtp.RtpParticipant;
import com.bluejay.rtp.RtpSession;
import com.bluejay.rtp.RtpSessionDataListener;
import com.bluejay.rtp.SingleParticipantSession;

import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 100;

    private static final String TAG = "MainActivity";
    private AudioSource audioSource;

    private Observable<byte[]> audioStream$;
    private AudioCommand audioCommand;
    private Observable<String> commandStream$;

    private CompositeDisposable cd = new CompositeDisposable();

    class PlaySound implements Runnable
    {

        private LinkedList<byte[]> stream;
        public PlaySound(LinkedList<byte[]> stream) {
            this.stream = stream;
        }
        @Override
        public void run()
        {
            AudioPlayer player = AudioPlayer.getInstance();
            player.init();
            player.play(this.stream);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.audioSource = new AudioSource(this);
        this.audioStream$ = audioSource.getAudioSource();
        this.audioCommand = new AudioCommand(this, this.audioStream$);
        this.commandStream$ = this.audioCommand.aWakeStream;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                return;
        } else {
            audioSource.startRecording();
            audioCommand.startRecognition();
        }






        Observable<Integer> volume$ = this.audioStream$.subscribeOn(Schedulers.computation())
                .map(new Function<byte[], Integer>() {
                    @Override
                    public Integer apply(byte[] bytes) throws Exception {
                        return calculateVolume(bytes, 16);
                    }
                });
        Observable<Boolean> talking$ = volume$
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
                });
        Observable<String> awake$ = this.commandStream$
                .observeOn(Schedulers.io())

//                .doOnNext(n -> Log.d(TAG, "Command " + n))
                .filter(comm -> comm.equals("go"))
                .switchMap(c -> Observable.timer(4, TimeUnit.SECONDS).map(new Function<Long, String>() {
                    @Override
                    public String apply(Long aLong) throws Exception {
                        return "";
                    }
                }).startWith(c)
                )
                .startWith("");

        Observable<byte[]> conversation$ = Observable.combineLatest(this.audioStream$, awake$, talking$, new Function3<byte[], String, Boolean, byte[]>() {
            @Override
            public byte[] apply(byte[] bytes, String s, Boolean talking) throws Exception {
                if (s.equals("go") && talking) return bytes;
                return new byte[0];
            }
        }).filter(b-> b.length > 0);


        final TextView volumeView = findViewById(R.id.volumeView);
        cd.add(
                volume$
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {

                        volumeView.setText(String.format(Locale.US, "Volume: %d", integer));
                    }
                })
        );

        final TextView talkingView = findViewById(R.id.talkingView);
        cd.add(
                talking$
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean talking) throws Exception {
                        talkingView.setText(String.format(Locale.US, "Talking: %s", talking? "Yes" : "No"));
                    }
                })
        );

        final TextView awakeView = findViewById(R.id.awakeView);
        cd.add(
                awake$.observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String awake) throws Exception {
                        awakeView.setText(String.format(Locale.US, "Awake: %s", awake.equals("go")? "Yes" : "No, say 'go' to wake me up"));
                    }
                })
        );


        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                LinkedList<byte[]> data = new LinkedList<>();
                AudioReceiver receiver = new AudioReceiver();

                AudioSender sender = AudioSender.getInstance(initRtc(receiver));

                cd.add(
                audioStream$.subscribeOn(Schedulers.io())
                        .subscribe(new Consumer<byte[]>() {
                            @Override
                            public void accept(byte[] bytes) throws Exception {
                                data.offer(bytes);
                            }
                        })
                );

                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        sender.send(data);
                    }
                }, "Sender").start();

                new Thread(new PlaySound(receiver.receive()), "Player").start();

//                initRtc(conversation$);
            }
        });

        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                audioSource.stopRecording();
            }
        });






    }

//    private  SingleParticipantSession session;




    private RtpSession initRtc(AudioReceiver receiver) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
                String remoteAddress = ((EditText)findViewById(R.id.editText2)).getText().toString();
                String remotePort = ((EditText)findViewById(R.id.editText1)).getText().toString();

                RtpParticipant localP = RtpParticipant.createReceiver("0.0.0.0", 12345, 11113);
                RtpParticipant remoteP = RtpParticipant.createReceiver(remoteAddress, Integer.parseInt(remotePort) , 21112);

                RtpSession session = new SingleParticipantSession("id", 1, localP, remoteP);

                session.addDataListener(receiver);

                try {
                    session.init();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return session;
//            }
//        }, "RTC thread").start();


    }


//    private void send() {


//        this.audioSource.onNext(new byte[]{(byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5});

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

//    }

//    public int getValidSampleRates() {
//        for (int rate : new int[] {16000, 8000}) {  // add the rates you wish to check against
//            int bufferSize = AudioRecord.getMinBufferSize(rate, mChannelConfig, AudioFormat.ENCODING_PCM_16BIT);
//            if (bufferSize > 0) {
//                return bufferSize;
//
//            }
//        }
//        return 0;
//    }

/*
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
*/
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
                    audioSource.startRecording();
                    audioCommand.startRecognition();
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
