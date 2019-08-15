
package com.bluejay.rtp;


public interface RtpSession {

    String getId();

    int getPayloadType();

    boolean init();

    boolean sendData(byte[] data, long timestamp);

    void terminate();

    RtpParticipant getLocalParticipant();

    RtpParticipant getRemoteParticipant();

    void addDataListener(RtpSessionDataListener listener);

    void removeDataListener(RtpSessionDataListener listener);

}
