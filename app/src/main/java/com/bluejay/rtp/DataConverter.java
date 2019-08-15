package com.bluejay.rtp;

public interface DataConverter<I, O> {

    public O convert(I in);
}
