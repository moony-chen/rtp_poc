package com.example.rtp_poc;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MP3Decoder {

    private MediaCodec codec;
    private Queue<byte[]> audio_q;
    private Queue<byte[]> audio_q_out = new ConcurrentLinkedQueue<>();

    private boolean running = false;

    private Thread codecInput;
    private Thread codecOutput;

    public MP3Decoder(Queue<byte[]> audio_q) throws IOException {
        codec = MediaCodec.createDecoderByType("audio/mpeg");
        this.audio_q = audio_q;
    }

    private class CodecEnqueue implements Runnable {

        ByteBuffer[] inputDatas = codec.getInputBuffers();

        @Override
        public void run() {
            while (running) {
                byte[] data = audio_q.poll();

                if (data != null && data.length > 0) {
                    int inputIndex = codec.dequeueInputBuffer(-1);
                    //Input data
                    ByteBuffer inputBuffer = inputDatas[inputIndex];
                    inputBuffer.clear();
                    inputBuffer.put(data);
                    codec.queueInputBuffer(inputIndex, 0, data.length, 0, 0);
                }
            }
        }
    }

    private class CodecDequeue implements Runnable {
        ByteBuffer[] outputDatas = codec.getOutputBuffers();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        @Override
        public void run() {

            ByteBuffer outputBuffer;
            byte[] chunkPCM;
            while (running) {
                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, -1);

                outputBuffer = outputDatas[outputIndex];
                chunkPCM = new byte[bufferInfo.size];
                outputBuffer.get(chunkPCM);
                outputBuffer.clear();

                audio_q_out.offer(chunkPCM);

                codec.releaseOutputBuffer(outputIndex, false);
            }
        }
    }

    public void start() {
        MediaFormat mp3 = MediaFormat.createAudioFormat("audio/mpeg", 44100, 2);
        codec.configure(mp3, null, null, 0);
        codec.start();
        running = true;
        codecInput = new Thread(new CodecEnqueue());
        codecOutput = new Thread(new CodecDequeue());
        codecInput.start();
        codecOutput.start();
    }

    public void stop() {
        codec.stop();
        running = false;
    }

    public Queue<byte[]> getAudioOut() {
        return audio_q_out;
    }


    public static void main(String[] args) throws Exception {
        File file = new File("/Users/fangchen/Downloads/04 .The Wind Forest.mp3");
        byte[] fileData = new byte[(int) file.length()];
        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        dis.readFully(fileData);
        ByteBuffer buffer = ByteBuffer.wrap(fileData);
        byte[] bytes = new byte[1024];

        LinkedList<byte[]> q = new LinkedList<>();

        int l = buffer.remaining();
        while (l > 0) {
            if (l >= 1024) {
                buffer.get(bytes);
                q.offer(bytes);
            } else {
                byte[] rem = new byte[l];
                buffer.get(rem);
                q.offer(rem);
            }
            l = buffer.remaining();
        }

        MP3Decoder decoder = new MP3Decoder(q);
        Queue<byte[]> audioOut = decoder.getAudioOut();
        decoder.start();

        Thread.sleep(20000);

        FileOutputStream fos = new FileOutputStream("/Users/fangchen/Downloads/04 .The Wind Forest.pcm");

        byte[] outs = audioOut.poll();
        while (outs != null && outs.length > 0) {
            fos.write(outs);
        }
        fos.close();

    }
}
