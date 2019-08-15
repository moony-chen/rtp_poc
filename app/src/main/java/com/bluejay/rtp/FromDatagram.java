package com.bluejay.rtp;

import java.net.DatagramPacket;

public class FromDatagram implements DataConverter<DatagramPacket, DataPacket> {

    @Override
    public DataPacket convert(DatagramPacket in) {
        return DataPacket.decode(in.getData());
    }
}
