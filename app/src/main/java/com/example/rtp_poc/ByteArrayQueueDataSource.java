package com.example.rtp_poc;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public class ByteArrayQueueDataSource extends BaseDataSource {
    private final Queue<byte[]> data;
    private byte[] cache = new byte[0];

    private @Nullable
    Uri uri;
    private int readPosition;
    private int bytesRemaining = 100;
    private boolean opened;

    /**
     * @param data The data to be read.
     */
    public ByteArrayQueueDataSource(Queue<byte[]> data) {
        super(/* isNetwork= */ false);
        Assertions.checkNotNull(data);
        this.data = data;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);
        readPosition = (int) dataSpec.position;
        bytesRemaining = 65536;
        opened = true;
        transferStarted(dataSpec);
        return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int copied = 0;

        if (cache.length >= readLength) { // cache is enough for buffer
//            System.out.println("cache is enough for buffer offset:" + offset+ ", readLength:" + readLength);
            System.arraycopy(cache, 0, buffer, offset, readLength);
            byte[] cacheN = new byte[cache.length - readLength];
            System.arraycopy(cache, readLength, cacheN, 0, cacheN.length);
            cache = cacheN;
            copied = readLength;
        } else {
//            System.out.println("poll for buffer offset:" + offset+ ", readLength:" + readLength);
            System.arraycopy(cache, 0, buffer, offset, cache.length);
            copied += cache.length;
            cache = new byte[0];

            byte[] peek = data.peek();
            while (peek != null && copied < readLength) {
                byte[] poll = data.poll();
                if (poll.length + copied <= readLength) {
                    System.arraycopy(poll, 0, buffer, offset + copied, poll.length);
                    copied += poll.length;
                } else {
                    System.arraycopy(poll, 0, buffer, offset + copied, readLength - copied);
                    cache = new byte[poll.length - (readLength - copied)];
                    System.arraycopy(poll, readLength - copied, cache, 0, cache.length);
                    copied = readLength;
                }
            }
        }

//        bytesRemaining = cache.length + (data.peek() != null ? data.peek().length : 0);


        readPosition += copied;
        bytesTransferred(copied);
        return copied;
    }

    @Override
    public @Nullable Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        if (opened) {
            opened = false;
            transferEnded();
        }
        uri = null;
    }

    /*

    public static void main(String[] args) throws Exception {

        byte[] fileData2 = new byte[37581];
        File file = new File("/Users/moonychen/Downloads/20170525093951.mp3");

        InputStream mp3file2 = new FileInputStream(file);
//            mp3file.
        DataInputStream dis2 = new DataInputStream(mp3file2);
        dis2.readFully(fileData2);

        String file2Str = Arrays.toString(fileData2);
//        System.out.println(file2Str);




        LinkedList<byte[]> queue = new LinkedList<>();

        byte[] fileData = new byte[1024];
        InputStream mp3file = new FileInputStream(new File("/Users/moonychen/Downloads/20170525093951.mp3"));
//            mp3file.
        DataInputStream dis = new DataInputStream(mp3file);
        int sizeRead = dis.read(fileData);
        while (sizeRead > 0) {

            queue.offer(Arrays.copyOf(fileData, sizeRead));
            sizeRead = dis.read(fileData);
        }
        dis.close();


        ByteArrayQueueDataSource subject = new ByteArrayQueueDataSource(queue);
        int acc = 0;
        int total = 37581;
        byte[] result = new byte[total];
        while (acc < total) {
            int r = ThreadLocalRandom.current().nextInt(0, 512 + 1);
            if (total - acc < r) r = total - acc;
            subject.read(result, acc, r);


            acc += r;
            String v1 = Arrays.toString(Arrays.copyOf(result, acc));
            String v2 = Arrays.toString(Arrays.copyOf(fileData2, acc));
            if (!v1.equals(v2)) {
                throw new Exception();
            }
        }

        String file3Str = Arrays.toString(result);
        System.out.println(file3Str);

        if (file3Str.equals(file2Str)) {
            System.out.println("hoooray");
        }
    }
    */
}
