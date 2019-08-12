package com.example.rtp_poc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReentrantLock;

public class RoundRobinBuffer {

    private byte[] buffer;
    private int pos = 0;
    private int length ;

    private final ReentrantLock bufferLock = new ReentrantLock();

    public RoundRobinBuffer(int length) {
        this.length = length;
        buffer = new byte[length];
    }

    public void put(byte[] array) {
        bufferLock.lock();
        try {
            if (length - pos - array.length >= 0) {
                System.arraycopy(array, 0, buffer, pos, array.length);
                pos = pos + array.length;
            } else {
                System.arraycopy(array, 0, buffer, pos, length - pos);
                System.arraycopy(array, length - pos, buffer, 0, array.length - (length - pos));
                pos = array.length - (length - pos);
            }
        } finally {
            bufferLock.unlock();
        }
    }

    public byte[] getBuffer() {
        byte[] result = new byte[length];
        bufferLock.lock();
        try {
            System.arraycopy(buffer, pos, result, 0, length - pos);
            System.arraycopy(buffer, 0, result, length - pos,  pos);
        } finally {
            bufferLock.unlock();
        }
        return result;
    }

    public short[] getShortBuffer() {
        byte[] result = getBuffer();
        short[] shorts = new short[result.length /2 ];

        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        return shorts;
    }


}
