/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bluejay.rtp;

import android.util.Log;

import com.biasedbit.efflux.network.DataHandler;
import com.biasedbit.efflux.network.DataPacketDecoder;
import com.biasedbit.efflux.network.DataPacketEncoder;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.ParticipantDatabase;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.SingleParticipantDatabase;
import com.biasedbit.efflux.session.AbstractRtpSession;

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.DatagramPacketDecoder;
import io.netty.handler.codec.DatagramPacketEncoder;
import io.netty.util.HashedWheelTimer;


public class SingleParticipantSession implements RtpSession {

    protected static final int BANDWIDTH_LIMIT = 256;
    protected static final int SEND_BUFFER_SIZE = 1024;
    protected static final int RECEIVE_BUFFER_SIZE = 1024;


    private final String id;
    private final int payloadType;
    private final RtpParticipant localParticipant;
    private final RtpParticipant receiver;
    private int sendBufferSize;
    private int receiveBufferSize;

    // configuration --------------------------------------------------------------------------------------------------

    private final AtomicBoolean running;
    protected final AtomicInteger sequence;

    private boolean sendToLastOrigin;
    private boolean ignoreFromUnknownSsrc;

    // internal vars --------------------------------------------------------------------------------------------------
    private DatagramSocket clientSocket;



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

        DataPacket packet = new DataPacket();
        // Other fields will be set by sendDataPacket()
        packet.setTimestamp(timestamp);
        packet.setData(data);
        packet.setMarker(false);

        return this.sendDataPacket(packet);
    }

    public boolean sendDataPacket(DataPacket packet) {
        if (!this.running.get()) {
            return false;
        }

        packet.setPayloadType(this.payloadType);
        packet.setSsrc(this.localParticipant.getSsrc());
        packet.setSequenceNumber(this.sequence.incrementAndGet());
        this.internalSendData(packet);
        return true;
    }

    protected void internalSendData(DataPacket packet) {
        try {


        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Tag", "Failed to send {} to {} in session with id {}.", this.id, this.receiver.getInfo());
        }
    }

    @Override
    public void terminate() {
        try {
            this.running.set(false);
            channel.closeFuture().sync();
            group.shutdownGracefully().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }




    // DataPacketReceiver ---------------------------------------------------------------------------------------------

    @Override
    public void dataPacketReceived(SocketAddress origin, DataPacket packet) {
        if (!this.receivedPackets.getAndSet(true)) {
            // If this is the first packet then setup the SSRC for this participant (we didn't know it yet).
            this.receiver.getInfo().setSsrc(packet.getSsrc());
            LOG.trace("First packet received from remote source, updated SSRC to {}.", packet.getSsrc());
        } else if (this.ignoreFromUnknownSsrc && (packet.getSsrc() != this.receiver.getInfo().getSsrc())) {
            LOG.trace("Discarded packet from unexpected SSRC: {} (expected was {}).",
                      packet.getSsrc(), this.receiver.getInfo().getSsrc());
            return;
        }

        super.dataPacketReceived(origin, packet);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public RtpParticipant getRemoteParticipant() {
        return this.receiver;
    }

    public boolean isSendToLastOrigin() {
        return sendToLastOrigin;
    }

    public void setSendToLastOrigin(boolean sendToLastOrigin) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.sendToLastOrigin = sendToLastOrigin;
    }

    public boolean isIgnoreFromUnknownSsrc() {
        return ignoreFromUnknownSsrc;
    }

    public void setIgnoreFromUnknownSsrc(boolean ignoreFromUnknownSsrc) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.ignoreFromUnknownSsrc = ignoreFromUnknownSsrc;
    }


}
