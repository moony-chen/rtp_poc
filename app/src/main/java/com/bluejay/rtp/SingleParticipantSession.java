package com.bluejay.rtp;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;



public class SingleParticipantSession implements RtpSession {

    private static final String TAG = "RtpSession";

    protected static final int SEND_BUFFER_SIZE = 1500;
    protected static final int RECEIVE_BUFFER_SIZE = 1500;


    private final String id;
    private final int payloadType;
    private final RtpParticipant localParticipant;
    private final RtpParticipant receiver;
    private int sendBufferSize;
    private int receiveBufferSize;

    // configuration --------------------------------------------------------------------------------------------------

    private final AtomicBoolean running;
    protected final AtomicInteger sequence;

    protected final List<RtpSessionDataListener> dataListeners;

    // internal vars --------------------------------------------------------------------------------------------------
    private DatagramSocket clientSocket;

    private Thread sendThread;
    private RtpSender rtpSender;
    private Thread receiveThread;
    private RtpReceiver rtpReceiver;



    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant) {
        if ((payloadType < 0) || (payloadType > 127)) {
            throw new IllegalArgumentException("PayloadType must be in range [0;127]");
        }
        this.id = id;
        this.payloadType = payloadType;
        this.localParticipant = localParticipant;
        this.receiver = remoteParticipant;

        this.sendBufferSize = SEND_BUFFER_SIZE;
        this.receiveBufferSize = RECEIVE_BUFFER_SIZE;

        this.running = new AtomicBoolean(false);
        this.sequence = new AtomicInteger(0);
        this.dataListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPayloadType() {
        return this.payloadType;
    }


    @Override
    public RtpParticipant getLocalParticipant() {
        return this.localParticipant;
    }


    @Override
    public boolean init(){
        if (this.running.get()) {
            return true;
        }

        try {
            clientSocket = new DatagramSocket();
            clientSocket.setReceiveBufferSize(5120);
//            clientSocket.setSendBufferSize(this.sendBufferSize);
            clientSocket.setSoTimeout(8000);

            rtpSender = new RtpSender(clientSocket, new ToDatagram(this.receiver));
            sendThread = new Thread(rtpSender);
            sendThread.start();

            rtpReceiver = new RtpReceiver(dataListeners, clientSocket, this);
            receiveThread = new Thread(rtpReceiver);
            receiveThread.start();


            this.running.set(true);

            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }

    }


    public boolean sendData(byte[] data, long timestamp) {
        if (!this.running.get()) {
            return false;
        }
        if (data == null || data.length == 0) return true;

        DataPacket packet = new DataPacket();
        // Other fields will be set by sendDataPacket()
        packet.setTimestamp(timestamp);
        packet.setData(data);
        packet.setMarker(false);

        packet.setPayloadType(this.payloadType);
        packet.setSsrc(this.localParticipant.getSsrc());
        packet.setSequenceNumber(this.sequence.incrementAndGet());
        this.internalSendData(packet);
        return true;
    }


    protected void internalSendData(DataPacket packet) {
        try {


            rtpSender.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to send" );
        }
    }

    @Override
    public void terminate() {
        try {
            this.running.set(false);
            clientSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // getters & setters ----------------------------------------------------------------------------------------------

    public RtpParticipant getRemoteParticipant() {
        return this.receiver;
    }


    @Override
    public void addDataListener(RtpSessionDataListener listener) {
        this.dataListeners.add(listener);
    }

    @Override
    public void removeDataListener(RtpSessionDataListener listener) {
        this.dataListeners.remove(listener);
    }

    private class RtpSender implements Runnable {
        private Queue<DataPacket> queue = new ConcurrentLinkedQueue<>();
        private boolean stopped = false;

        private DatagramSocket clientSocket;
        private DataConverter<DataPacket, DatagramPacket> converter;

        public RtpSender(DatagramSocket clientSocket, DataConverter<DataPacket, DatagramPacket> converter) {
            this.clientSocket = clientSocket;
            this.converter = converter;
        }

        public void send(DataPacket dataPacket) {
            this.queue.offer(dataPacket);
        }

        public boolean isStopped() {
            return stopped;
        }

        public void setStopped(boolean stopped) {
            this.stopped = stopped;
        }

        @Override
        public void run() {
            while (!stopped) {
                try {
                    if (!queue.isEmpty()) {
                        DataPacket packet = queue.poll();
                        clientSocket.send(converter.convert(packet));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to send" );
                }
            }
        }
    }


    private class RtpReceiver implements Runnable {
        private List<RtpSessionDataListener> listeners;
        private boolean stopped = false;
        private DatagramSocket clientSocket;
        private RtpSession session;
        private DataConverter<DatagramPacket, DataPacket> converter = new FromDatagram();

        public RtpReceiver(List<RtpSessionDataListener> listeners, DatagramSocket clientSocket, RtpSession session) {
            this.listeners = listeners;
            this.clientSocket = clientSocket;
            this.session = session;
        }

        @Override
        public void run() {
            while(!stopped) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[RECEIVE_BUFFER_SIZE], RECEIVE_BUFFER_SIZE);
                    clientSocket.receive(packet);
                    RtpParticipant remoteParticipant = session.getRemoteParticipant();


                    if (!packet.getSocketAddress().equals(remoteParticipant.getDataDestination())) continue;

                    for (RtpSessionDataListener listener: listeners) {
                        listener.dataPacketReceived(session, remoteParticipant, converter.convert(packet));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to receive" );
                }
            }
        }
    }
}
