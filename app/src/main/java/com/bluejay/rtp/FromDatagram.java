package com.bluejay.rtp;

import java.net.DatagramPacket;
import java.util.Arrays;

public class FromDatagram implements DataConverter<DatagramPacket, DataPacket> {

    @Override
    public DataPacket convert(DatagramPacket in) {
        return DataPacket.decode(Arrays.copyOfRange(in.getData(), 0, in.getLength()));
    }
}
