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

package com.biasedbit.efflux.session;

import com.biasedbit.efflux.network.DataHandler;
import com.biasedbit.efflux.network.DataPacketDecoder;
import com.biasedbit.efflux.network.DataPacketEncoder;
import com.biasedbit.efflux.packet.CompoundControlPacket;
import com.biasedbit.efflux.packet.ControlPacket;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.participant.ParticipantDatabase;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.SingleParticipantDatabase;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;

/**
 * Implementation that only supports two participants, a local and the remote.
 * <p/>
 * This session is ideal for calls with only two participants in NAT scenarions, where often the IP and ports negociated
 * in the SDP aren't the real ones (due to NAT restrictions and clients not supporting ICE).
 * <p/>
 * If data is received from a source other than the expected one, this session will automatically update the destination
 * IP and newly sent packets will be addressed to that new IP rather than the old one.
 * <p/>
 * If more than one source is used to send data for this session it will often get "confused" and keep redirecting
 * packets to the last source from which it received.
 * <p>
 * This is <strong>NOT</strong> a fully RFC 3550 compliant implementation, but rather a special purpose one for very
 * specific scenarios.
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class SingleParticipantSession extends AbstractRtpSession {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean SEND_TO_LAST_ORIGIN = true;
    private static final boolean IGNORE_FROM_UNKNOWN_SSRC = true;

    // configuration --------------------------------------------------------------------------------------------------

    private final RtpParticipant receiver;
    private boolean sendToLastOrigin;
    private boolean ignoreFromUnknownSsrc;

    // internal vars --------------------------------------------------------------------------------------------------

    private final AtomicBoolean receivedPackets;

    private Channel channel;
    private EventLoopGroup group = new NioEventLoopGroup();

    // constructors ---------------------------------------------------------------------------------------------------

    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant) {
        this(id, payloadType, localParticipant, remoteParticipant, null);
    }


    public SingleParticipantSession(String id, int payloadType, RtpParticipant localParticipant,
                                    RtpParticipant remoteParticipant, HashedWheelTimer timer) {
        super(id, payloadType, localParticipant, timer);
        if (!remoteParticipant.isReceiver()) {
            throw new IllegalArgumentException("Remote participant must be a receiver (data & control addresses set)");
        }
        ((SingleParticipantDatabase) this.participantDatabase).setParticipant(remoteParticipant);
        this.receiver = remoteParticipant;
        this.receivedPackets = new AtomicBoolean(false);
        this.sendToLastOrigin = SEND_TO_LAST_ORIGIN;
        this.ignoreFromUnknownSsrc = IGNORE_FROM_UNKNOWN_SSRC;
    }

    @Override
    public boolean init(){
        if (this.running.get()) {
            return true;
        }

//        SocketAddress remote = new InetSocketAddress("192.168.1.108", 53063);

        this.dataBootstrap = new Bootstrap();
        this.dataBootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .remoteAddress(this.receiver.getDataDestination())
                .handler(new ChannelInitializer<DatagramChannel>() {

                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline().addLast("decoder", new DataPacketDecoder());
                        ch.pipeline().addLast("encoder", DataPacketEncoder.getInstance());
                        ch.pipeline().addLast("handler", new DataHandler(SingleParticipantSession.this));
                    }
                })
                .option(ChannelOption.SO_RCVBUF, this.receiveBufferSize)
                .option(ChannelOption.SO_SNDBUF, this.sendBufferSize);

        try {
            ChannelFuture f = this.dataBootstrap.connect().sync();
            channel = f.channel();
            this.running.set(true);

            return true;
        } catch (Exception e){
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
            // This assumes that the sender is sending is sending from the same ports where its expecting to receive.
            // Can be dangerous if the other end fully respects the RFC and supports ICE, but this is nearly the only
            // workaround that will work if the other end doesn't support ICE and is behind a NAT.

            this.channel.writeAndFlush(packet).sync();
            this.sentOrReceivedPackets.set(true);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Failed to send {} to {} in session with id {}.", this.id, this.receiver.getInfo());
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

    // RtpSession -----------------------------------------------------------------------------------------------------

    @Override
    public boolean addReceiver(RtpParticipant remoteParticipant) {
        if (this.receiver.equals(remoteParticipant)) {
            return true;
        }

        // Sorry, "there can be only one".
        return false;
    }

    @Override
    public boolean removeReceiver(RtpParticipant remoteParticipant) {
        // No can do.
        return false;
    }

    @Override
    public RtpParticipant getRemoteParticipant(long ssrc) {
        if (ssrc == this.receiver.getInfo().getSsrc()) {
            return this.receiver;
        }

        return null;
    }

    @Override
    public Map<Long, RtpParticipant> getRemoteParticipants() {
        Map<Long, RtpParticipant> map = new HashMap<Long, RtpParticipant>();
        map.put(this.receiver.getSsrc(), this.receiver);
        return map;
    }

    // AbstractRtpSession ---------------------------------------------------------------------------------------------

    @Override
    protected ParticipantDatabase createDatabase() {
        return new SingleParticipantDatabase(this.id);
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
