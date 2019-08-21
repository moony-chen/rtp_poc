package com.example.rtp_poc;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;

import java.io.IOException;
import java.util.Queue;

public class ByteArrayQueueDataSource extends BaseDataSource {
    private final Queue<byte[]> data;
    private byte[] cache = new byte[0];

    private @Nullable
    Uri uri;
    private int readPosition;
    private int bytesRemaining;
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
            System.arraycopy(cache, 0, buffer, offset, readLength);
            byte[] cacheN = new byte[cache.length - readLength];
            System.arraycopy(cache, readLength, cacheN, 0, cacheN.length);
            cache = cacheN;
            copied = readLength;
        } else {
            System.arraycopy(cache, 0, buffer, offset, cache.length);
            cache = new byte[0];
            copied = cache.length;
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

        if (data.peek() == null && cache.length == 0) {
            return C.RESULT_END_OF_INPUT;
        }


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
}
