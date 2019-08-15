package com.bluejay.rtp;

import java.net.DatagramPacket;

public class ToDatagram implements DataConverter<DataPacket, DatagramPacket> {

    private RtpParticipant receiver;

    public ToDatagram(RtpParticipant receiver) {
        this.receiver = receiver;
    }

    @Override
    public DatagramPacket convert(DataPacket in) {
        byte[] bytes = in.encode();

        DatagramPacket pack = new DatagramPacket(bytes, bytes.length, this.receiver.getDataDestination());
        return pack;
    }
}
