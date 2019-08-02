package com.biasedbit.efflux;

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.RtpParticipantInfo;
import com.biasedbit.efflux.session.RtpSession;
import com.biasedbit.efflux.session.RtpSessionDataListener;
import com.biasedbit.efflux.session.SingleParticipantSession;

/**
 * Created by fangchen on 7/28/19.
 */
public class Main {

    public static void main(String[] args) throws  Exception {
        RtpParticipant localP = RtpParticipant.createReceiver("locahost", 11111, 11112);
        RtpParticipant remoteP = RtpParticipant.createReceiver("localhost", 56354, 21112);

        SingleParticipantSession session = new SingleParticipantSession("id", 1, localP, remoteP);
        session.addDataListener(new RtpSessionDataListener() {
            @Override
            public void dataPacketReceived(RtpSession session, RtpParticipantInfo participant, DataPacket packet) {
                Logger.getLogger(Main.class).debug(packet.getDataAsArray().toString());
            }
        });

        DataPacket dp = new DataPacket();
        dp.setPayloadType(1);
        dp.setSequenceNumber(1);
        dp.setData(new byte[]{(byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5, (byte) 0xd5});
//        session.sendDataPacket(dp);
        Thread.sleep(2000);
    }
}
