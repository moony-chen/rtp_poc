package com.biasedbit.efflux;

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.biasedbit.efflux.session.SingleParticipantSession;

import java.util.Arrays;
import java.util.Date;

import io.netty.buffer.ByteBuf;

/**
 * Created by fangchen on 7/28/19.
 */
public class Main {

    public static void main(String[] args) throws  Exception {

        long time = new Date().getTime();

        DataPacket dp = new DataPacket();
        dp.setPayloadType(111);
        dp.setSequenceNumber(256);
        dp.setSsrc(2838938934L);
        dp.setTimestamp(time);
        dp.setData(new byte[]{(byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5});

        byte[] bufferO = dp.encode().array();

        com.bluejay.rtp.DataPacket dpN = new com.bluejay.rtp.DataPacket();
        dpN.setPayloadType(111);
        dpN.setSequenceNumber(256);
        dpN.setSsrc(2838938934L);
        dpN.setTimestamp(time);
        dpN.setData(new byte[]{(byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5});



        System.out.println(Arrays.toString(bufferO));

        byte[] bufferN = dpN.encode();
        System.out.println(Arrays.toString(bufferN));

    }
}
