package com.bluejay.rtp;


import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class RtpParticipant {

    private static final Random RANDOM = new Random(System.nanoTime());

    // constants ------------------------------------------------------------------------------------------------------

    private static final int VALID_PACKETS_UNTIL_VALID_PARTICIPANT = 3;

    // configuration --------------------------------------------------------------------------------------------------


    // internal vars --------------------------------------------------------------------------------------------------

    private SocketAddress dataDestination;
    private SocketAddress controlDestination;
    private SocketAddress lastDataOrigin;
    private SocketAddress lastControlOrigin;
    private long lastReceptionInstant;
    private long byeReceptionInstant;
    private int lastSequenceNumber;
    private boolean receivedSdes;
    private final AtomicLong receivedByteCounter;
    private final AtomicLong receivedPacketCounter;
    private final AtomicInteger validPacketCounter;

    private long ssrc;

    // constructors ---------------------------------------------------------------------------------------------------

    private RtpParticipant() {

        this.lastSequenceNumber = -1;
        this.lastReceptionInstant = 0;
        this.byeReceptionInstant = 0;

        this.receivedByteCounter = new AtomicLong();
        this.receivedPacketCounter = new AtomicLong();
        this.validPacketCounter = new AtomicInteger();

        this.ssrc = generateNewSsrc();
    }

    public static long generateNewSsrc() {
        return RANDOM.nextInt(Integer.MAX_VALUE);
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static RtpParticipant createReceiver(String host, int dataPort, int controlPort) {
        RtpParticipant participant = new RtpParticipant();

        if ((dataPort < 0) || (dataPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }
        if ((controlPort < 0) || (controlPort > 65536)) {
            throw new IllegalArgumentException("Invalid port number; use range [0;65536]");
        }

        participant.dataDestination = new InetSocketAddress(host, dataPort);
        participant.controlDestination = new InetSocketAddress(host, controlPort);

        return participant;
    }


    public static RtpParticipant createFromUnexpectedDataPacket(SocketAddress origin, DataPacket packet) {
        RtpParticipant participant = new RtpParticipant();
        participant.lastDataOrigin = origin;

        return participant;
    }



    // getters & setters ----------------------------------------------------------------------------------------------

    public long getSsrc() {
        return this.ssrc;
    }


    public long getLastReceptionInstant() {
        return lastReceptionInstant;
    }

    public long getByeReceptionInstant() {
        return byeReceptionInstant;
    }

    public int getLastSequenceNumber() {
        return lastSequenceNumber;
    }

    public void setLastSequenceNumber(int lastSequenceNumber) {
        this.lastSequenceNumber = lastSequenceNumber;
    }

    public boolean receivedBye() {
        return this.byeReceptionInstant > 0;
    }

    public long getReceivedPackets() {
        return this.receivedPacketCounter.get();
    }

    public long getReceivedBytes() {
        return this.receivedByteCounter.get();
    }

    public boolean hasReceivedSdes() {
        return receivedSdes;
    }

    public SocketAddress getDataDestination() {
        return dataDestination;
    }

    public void setDataDestination(SocketAddress dataDestination) {
        if (dataDestination == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.dataDestination = dataDestination;
    }

    public SocketAddress getControlDestination() {
        return controlDestination;
    }

    public void setControlDestination(SocketAddress controlDestination) {
        if (dataDestination == null) {
            throw new IllegalArgumentException("Argument cannot be null");
        }
        this.controlDestination = controlDestination;
    }

    public SocketAddress getLastDataOrigin() {
        return lastDataOrigin;
    }

    public void setLastDataOrigin(SocketAddress lastDataOrigin) {
        this.lastDataOrigin = lastDataOrigin;
    }

    public SocketAddress getLastControlOrigin() {
        return lastControlOrigin;
    }

    public void setLastControlOrigin(SocketAddress lastControlOrigin) {
        this.lastControlOrigin = lastControlOrigin;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RtpParticipant)) {
            return false;
        }

        RtpParticipant that = (RtpParticipant) o;
        return this.dataDestination.equals(that.dataDestination);
    }

    @Override
    public int hashCode() {
        int result = dataDestination.hashCode();
        result = 31 * result + controlDestination.hashCode();
        return result;
    }

}
