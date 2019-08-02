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

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.network.DataHandler;
import com.biasedbit.efflux.network.DataPacketDecoder;
import com.biasedbit.efflux.network.DataPacketEncoder;
import com.biasedbit.efflux.packet.AbstractReportPacket;
import com.biasedbit.efflux.packet.AppDataPacket;
import com.biasedbit.efflux.packet.ByePacket;
import com.biasedbit.efflux.packet.CompoundControlPacket;
import com.biasedbit.efflux.packet.ControlPacket;
import com.biasedbit.efflux.packet.DataPacket;
import com.biasedbit.efflux.packet.ReceiverReportPacket;
import com.biasedbit.efflux.packet.ReceptionReport;
import com.biasedbit.efflux.packet.SdesChunk;
import com.biasedbit.efflux.packet.SdesChunkItems;
import com.biasedbit.efflux.packet.SenderReportPacket;
import com.biasedbit.efflux.packet.SourceDescriptionPacket;
import com.biasedbit.efflux.participant.ParticipantDatabase;
import com.biasedbit.efflux.participant.ParticipantOperation;
import com.biasedbit.efflux.participant.RtpParticipant;
import com.biasedbit.efflux.participant.RtpParticipantInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public abstract class AbstractRtpSession implements RtpSession {

    // constants ------------------------------------------------------------------------------------------------------

    protected static final Logger LOG = Logger.getLogger(AbstractRtpSession.class);
    protected static final String VERSION = "efflux_0.4_15092010";

    // configuration defaults -----------------------------------------------------------------------------------------

    // TODO not working with USE_NIO = false
    protected static final boolean USE_NIO = true;
    protected static final boolean DISCARD_OUT_OF_ORDER = true;
    protected static final int BANDWIDTH_LIMIT = 256;
    protected static final int SEND_BUFFER_SIZE = 1500;
    protected static final int RECEIVE_BUFFER_SIZE = 1500;
    protected static final int MAX_COLLISIONS_BEFORE_CONSIDERING_LOOP = 3;
    protected static final boolean AUTOMATED_RTCP_HANDLING = true;
    protected static final boolean TRY_TO_UPDATE_ON_EVERY_SDES = true;
    protected static final int PARTICIPANT_DATABASE_CLEANUP = 10;

    // configuration --------------------------------------------------------------------------------------------------

    protected final String id;
    protected final int payloadType;
    protected final HashedWheelTimer timer;
    protected String host;
    protected boolean useNio;
    protected boolean discardOutOfOrder;
    protected int bandwidthLimit;
    protected int sendBufferSize;
    protected int receiveBufferSize;
    protected int maxCollisionsBeforeConsideringLoop;
    protected boolean automatedRtcpHandling;
    protected boolean tryToUpdateOnEverySdes;
    protected int participantDatabaseCleanup;

    // internal vars --------------------------------------------------------------------------------------------------

    protected final AtomicBoolean running;
    protected final RtpParticipant localParticipant;
    protected final ParticipantDatabase participantDatabase;
    protected final List<RtpSessionDataListener> dataListeners;
    protected final List<RtpSessionControlListener> controlListeners;
    protected final List<RtpSessionEventListener> eventListeners;
    protected Bootstrap dataBootstrap;
//    protected Bootstrap controlBootstrap;
    protected DatagramChannel dataChannel;
//    protected DatagramChannel controlChannel;
    protected final AtomicInteger sequence;
    protected final AtomicBoolean sentOrReceivedPackets;
    protected final AtomicInteger collisions;
    protected final AtomicLong sentByteCounter;
    protected final AtomicLong sentPacketCounter;
    protected int periodicRtcpSendInterval;
    protected final boolean internalTimer;

    // constructors ---------------------------------------------------------------------------------------------------

    public AbstractRtpSession(String id, int payloadType, RtpParticipant local) {
        this(id, payloadType, local, null);
    }

    public AbstractRtpSession(String id, int payloadType, RtpParticipant local, HashedWheelTimer timer) {
        if ((payloadType < 0) || (payloadType > 127)) {
            throw new IllegalArgumentException("PayloadType must be in range [0;127]");
        }

        if (!local.isReceiver()) {
            throw new IllegalArgumentException("Local participant must have its data & control addresses set");
        }

        this.id = id;
        this.payloadType = payloadType;
        this.localParticipant = local;
        this.participantDatabase = this.createDatabase();
        if (timer == null) {
            this.timer = new HashedWheelTimer(1, TimeUnit.SECONDS);
            this.internalTimer = true;
        } else {
            this.timer = timer;
            this.internalTimer = false;
        }

        this.running = new AtomicBoolean(false);
        this.dataListeners = new CopyOnWriteArrayList<RtpSessionDataListener>();
        this.controlListeners = new CopyOnWriteArrayList<RtpSessionControlListener>();
        this.eventListeners = new CopyOnWriteArrayList<RtpSessionEventListener>();
        this.sequence = new AtomicInteger(0);
        this.sentOrReceivedPackets = new AtomicBoolean(false);
        this.collisions = new AtomicInteger(0);
        this.sentPacketCounter = new AtomicLong(0);
        this.sentByteCounter = new AtomicLong(0);

        this.useNio = USE_NIO;
        this.discardOutOfOrder = DISCARD_OUT_OF_ORDER;
        this.bandwidthLimit = BANDWIDTH_LIMIT;
        this.sendBufferSize = SEND_BUFFER_SIZE;
        this.receiveBufferSize = RECEIVE_BUFFER_SIZE;
        this.maxCollisionsBeforeConsideringLoop = MAX_COLLISIONS_BEFORE_CONSIDERING_LOOP;
        this.automatedRtcpHandling = AUTOMATED_RTCP_HANDLING;
        this.tryToUpdateOnEverySdes = TRY_TO_UPDATE_ON_EVERY_SDES;
        this.participantDatabaseCleanup = PARTICIPANT_DATABASE_CLEANUP;
    }

    // RtpSession -----------------------------------------------------------------------------------------------------

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPayloadType() {
        return this.payloadType;
    }



    public class RtpDatasourceHander extends SimpleChannelInboundHandler {

        private RtpDatasource datasource;
        private Disposable disposable;


        public RtpDatasourceHander(RtpDatasource datasource) {
            super(true);
            this.datasource = datasource;
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            running.set(true);
//            ctx.writeAndFlush(Unpooled.copiedBuffer("test", CharsetUtil.UTF_8));
            datasource.subscribe(new Observer<byte[]>() {
                @Override
                public void onSubscribe(Disposable d) {
                    disposable = d;
                }

                @Override
                public void onNext(byte[] o) {
                    ctx.writeAndFlush(wrapData(o, new Date().getTime()));
//                    ctx.writeAndFlush(Unpooled.copiedBuffer("test", CharsetUtil.UTF_8));
                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onComplete() {
//                ctx.channel().closeFuture().sync();
                }
            });

        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            running.set(false);
            if(disposable!=null && !disposable.isDisposed()) disposable.dispose();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {

        }
    }

//    @Override
//    public void terminate() {
//        this.terminate(RtpSessionEventListener.TERMINATE_CALLED);
//    }

    DataPacket wrapData(byte[] data, long timestamp) {
        if (!this.running.get()) {
            return null;
        }

        DataPacket packet = new DataPacket();
        // Other fields will be set by sendDataPacket()
        packet.setTimestamp(timestamp);
        packet.setData(data);
        packet.setPayloadType(this.payloadType);
        packet.setSsrc(this.localParticipant.getSsrc());
        packet.setSequenceNumber(this.sequence.incrementAndGet());
        return packet;

    }


    @Override
    public RtpParticipant getLocalParticipant() {
        return this.localParticipant;
    }

    @Override
    public boolean addReceiver(RtpParticipant remoteParticipant) {
        return (remoteParticipant.getSsrc() != this.localParticipant.getSsrc()) &&
               this.participantDatabase.addReceiver(remoteParticipant);
    }

    @Override
    public boolean removeReceiver(RtpParticipant remoteParticipant) {
        return this.participantDatabase.removeReceiver(remoteParticipant);
    }

    @Override
    public RtpParticipant getRemoteParticipant(long ssrc) {
        return this.participantDatabase.getParticipant(ssrc);
    }

    @Override
    public Map<Long, RtpParticipant> getRemoteParticipants() {
        return this.participantDatabase.getMembers();
    }

    @Override
    public void addDataListener(RtpSessionDataListener listener) {
        this.dataListeners.add(listener);
    }

    @Override
    public void removeDataListener(RtpSessionDataListener listener) {
        this.dataListeners.remove(listener);
    }

//    @Override
//    public void addControlListener(RtpSessionControlListener listener) {
//        this.controlListeners.add(listener);
//    }
//
//    @Override
//    public void removeControlListener(RtpSessionControlListener listener) {
//        this.controlListeners.remove(listener);
//    }

    @Override
    public void addEventListener(RtpSessionEventListener listener) {
        this.eventListeners.add(listener);
    }

    @Override
    public void removeEventListener(RtpSessionEventListener listener) {
        this.eventListeners.remove(listener);
    }

    // DataPacketReceiver ---------------------------------------------------------------------------------------------

    @Override
    public void dataPacketReceived(SocketAddress origin, DataPacket packet) {
        if (!this.running.get()) {
            return;
        }

        if (packet.getPayloadType() != this.payloadType) {
            // Silently discard packets of wrong payload.
            return;
        }

        if (packet.getSsrc() == this.localParticipant.getSsrc()) {
            // Sending data to ourselves? Consider this a loop and bail out!
//            if (origin.equals(this.localParticipant.getDataDestination())) {
//                this.terminate(new Throwable("Loop detected: session is directly receiving its own packets"));
//                return;
//            } else if (this.collisions.incrementAndGet() > this.maxCollisionsBeforeConsideringLoop) {
//                this.terminate(new Throwable("Loop detected after " + this.collisions.get() + " SSRC collisions"));
//                return;
//            }

            long oldSsrc = this.localParticipant.getSsrc();
            long newSsrc = this.localParticipant.resolveSsrcConflict(packet.getSsrc());

            // A collision has been detected after packets were sent, resolve by updating the local SSRC and sending
            // a BYE RTCP packet for the old SSRC.
            // http://tools.ietf.org/html/rfc3550#section-8.2
            // If no packet was sent and this is the first being received then we can avoid collisions by switching
            // our own SSRC to something else (nothing else is required because the collision was prematurely detected
            // and avoided).
            // http://tools.ietf.org/html/rfc3550#section-8.1, last paragraph
//            if (this.sentOrReceivedPackets.getAndSet(true)) {
//                this.leaveSession(oldSsrc, "SSRC collision detected; rejoining with new SSRC.");
//                this.joinSession(newSsrc);
//            }

            LOG.warn("SSRC collision with remote end detected on session with id {}; updating SSRC from {} to {}.",
                     this.id, oldSsrc, newSsrc);
            for (RtpSessionEventListener listener : this.eventListeners) {
                listener.resolvedSsrcConflict(this, oldSsrc, newSsrc);
            }
        }

        // Associate the packet with a participant or create one.
        RtpParticipant participant = this.participantDatabase.getOrCreateParticipantFromDataPacket(origin, packet);
        if (participant == null) {
            // Depending on database implementation, it may chose not to create anything, in which case this packet
            // must be discarded.
            return;
        }

        // Should the packet be discarded due to out of order SN?
        if ((participant.getLastSequenceNumber() >= packet.getSequenceNumber()) && this.discardOutOfOrder) {
            LOG.trace("Discarded out of order packet from {} in session with id {} (last SN was {}, packet SN was {}).",
                      participant, this.id, participant.getLastSequenceNumber(), packet.getSequenceNumber());
            return;
        }

        // Update last SN for participant.
        participant.setLastSequenceNumber(packet.getSequenceNumber());
        participant.setLastDataOrigin(origin);

        // Finally, dispatch the event to the data listeners.
        for (RtpSessionDataListener listener : this.dataListeners) {
            listener.dataPacketReceived(this, participant.getInfo(), packet);
        }
    }
//
//    // ControlPacketReceiver ------------------------------------------------------------------------------------------
//
//    @Override
//    public void controlPacketReceived(SocketAddress origin, CompoundControlPacket packet) {
//        if (!this.running.get()) {
//            return;
//        }
//
//        if (!this.automatedRtcpHandling) {
//            for (RtpSessionControlListener listener : this.controlListeners) {
//                listener.controlPacketReceived(this, packet);
//            }
//
//            return;
//        }
//
//        for (ControlPacket controlPacket : packet.getControlPackets()) {
//            switch (controlPacket.getType()) {
//                case SENDER_REPORT:
//                case RECEIVER_REPORT:
//                    this.handleReportPacket(origin, (AbstractReportPacket) controlPacket);
//                    break;
//                case SOURCE_DESCRIPTION:
//                    this.handleSdesPacket(origin, (SourceDescriptionPacket) controlPacket);
//                    break;
//                case BYE:
//                    this.handleByePacket(origin, (ByePacket) controlPacket);
//                    break;
//                case APP_DATA:
//                    for (RtpSessionControlListener listener : this.controlListeners) {
//                        listener.appDataReceived(this, (AppDataPacket) controlPacket);
//                    }
//                default:
//                    // do nothing, unknown case
//            }
//        }
//    }

    // Runnable -------------------------------------------------------------------------------------------------------


    // protected helpers ----------------------------------------------------------------------------------------------

    protected void handleReportPacket(SocketAddress origin, AbstractReportPacket abstractReportPacket) {
        if (abstractReportPacket.getReceptionReportCount() == 0) {
            return;
        }

        RtpParticipant context = this.participantDatabase.getParticipant(abstractReportPacket.getSenderSsrc());
        if (context == null) {
            // Ignore; RTCP-SDES or RTP packet must first be received.
            return;
        }

        for (ReceptionReport receptionReport : abstractReportPacket.getReceptionReports()) {
            // Ignore all reception reports except for the one who pertains to the local participant (only data that
            // matters here is the link between this participant and ourselves).
            if (receptionReport.getSsrc() == this.localParticipant.getSsrc()) {
                // TODO
            }
        }

        // For sender reports, also handle the sender information.
        if (abstractReportPacket.getType().equals(ControlPacket.Type.SENDER_REPORT)) {
            SenderReportPacket senderReport = (SenderReportPacket) abstractReportPacket;
            // TODO
        }
    }

    protected void handleSdesPacket(SocketAddress origin, SourceDescriptionPacket packet) {
        for (SdesChunk chunk : packet.getChunks()) {
            RtpParticipant participant = this.participantDatabase.getOrCreateParticipantFromSdesChunk(origin, chunk);
            if (participant == null) {
                // Depending on database implementation, it may chose not to create anything, in which case this packet
                // must be discarded.
                return;
            }
            if (!participant.hasReceivedSdes() || this.tryToUpdateOnEverySdes) {
                participant.receivedSdes();
                // If this participant wasn't created from an SDES packet, then update its participant's description.
                if (participant.getInfo().updateFromSdesChunk(chunk)) {
                    for (RtpSessionEventListener listener : this.eventListeners) {
                        listener.participantDataUpdated(this, participant);
                    }
                }
            }
        }
    }

    protected void handleByePacket(SocketAddress origin, ByePacket packet) {
        for (Long ssrc : packet.getSsrcList()) {
            RtpParticipant participant = this.participantDatabase.getParticipant(ssrc);
            if (participant != null) {
                participant.byeReceived();
                for (RtpSessionEventListener listener : eventListeners) {
                    listener.participantLeft(this, participant);
                }
            }
        }
        LOG.trace("Received BYE for participants with SSRCs {} in session with id '{}' (reason: '{}').",
                  packet.getSsrcList(), this.id, packet. getReasonForLeaving());
    }

    protected abstract ParticipantDatabase createDatabase();



    protected AbstractReportPacket buildReportPacket(long currentSsrc, RtpParticipant context) {
        AbstractReportPacket packet;
        if (this.getSentPackets() == 0) {
            // If no packets were sent to this source, then send a receiver report.
            packet = new ReceiverReportPacket();
        } else {
            // Otherwise, build a sender report.
            SenderReportPacket senderPacket = new SenderReportPacket();
            senderPacket.setNtpTimestamp(0); // FIXME
            senderPacket.setRtpTimestamp(System.currentTimeMillis()); // FIXME
            senderPacket.setSenderPacketCount(this.getSentPackets());
            senderPacket.setSenderOctetCount(this.getSentBytes());
            packet = senderPacket;
        }
        packet.setSenderSsrc(currentSsrc);

        // If this source sent data, then calculate the link quality to build a reception report block.
        if (context.getReceivedPackets() > 0) {
            ReceptionReport block = new ReceptionReport();
            block.setSsrc(context.getInfo().getSsrc());
            block.setDelaySinceLastSenderReport(0); // FIXME
            block.setFractionLost((short) 0); // FIXME
            block.setExtendedHighestSequenceNumberReceived(0); // FIXME
            block.setInterArrivalJitter(0); // FIXME
            block.setCumulativeNumberOfPacketsLost(0); // FIXME
            packet.addReceptionReportBlock(block);
        }

        return packet;
    }
//
//    protected SourceDescriptionPacket buildSdesPacket(long currentSsrc) {
//        SourceDescriptionPacket sdesPacket = new SourceDescriptionPacket();
//        SdesChunk chunk = new SdesChunk(currentSsrc);
//
//        RtpParticipantInfo info = this.localParticipant.getInfo();
//        if (info.getCname() == null) {
//            info.setCname(new StringBuilder()
//                    .append("efflux/").append(this.id).append('@')
//                    .append(this.dataChannel.getLocalAddress()).toString());
//        }
//        chunk.addItem(SdesChunkItems.createCnameItem(info.getCname()));
//
//        if (info.getName() != null) {
//            chunk.addItem(SdesChunkItems.createNameItem(info.getName()));
//        }
//
//        if (info.getEmail() != null) {
//            chunk.addItem(SdesChunkItems.createEmailItem(info.getEmail()));
//        }
//
//        if (info.getPhone() != null) {
//            chunk.addItem(SdesChunkItems.createPhoneItem(info.getPhone()));
//        }
//
//        if (info.getLocation() != null) {
//            chunk.addItem(SdesChunkItems.createLocationItem(info.getLocation()));
//        }
//
//        if (info.getTool() == null) {
//            info.setTool(VERSION);
//        }
//        chunk.addItem(SdesChunkItems.createToolItem(info.getTool()));
//
//        if (info.getNote() != null) {
//            chunk.addItem(SdesChunkItems.createLocationItem(info.getNote()));
//        }
//        sdesPacket.addItem(chunk);
//
//        return sdesPacket;
////    }
//
//    protected synchronized void terminate(Throwable cause) {
//        // Always set to false, even it if was already set at false.
//        if (!this.running.getAndSet(false)) {
//            return;
//        }
//
//        if (this.internalTimer) {
//            this.timer.stop();
//        }
//
//        this.dataListeners.clear();
//        this.controlListeners.clear();
//
//        // Close data channel, send BYE RTCP packets and close control channel.
//        this.dataChannel.close();
//        this.leaveSession(this.localParticipant.getSsrc(), "Session terminated.");
//        this.controlChannel.close();
//
//        this.dataBootstrap.releaseExternalResources();
//        this.controlBootstrap.releaseExternalResources();
//        LOG.debug("RtpSession with id {} terminated.", this.id);
//
//        for (RtpSessionEventListener listener : this.eventListeners) {
//            listener.sessionTerminated(this, cause);
//        }
//        this.eventListeners.clear();
//    }

    protected void resetSendStats() {
        this.sentByteCounter.set(0);
        this.sentPacketCounter.set(0);
    }

    protected long incrementSentBytes(int delta) {
        if (delta < 0) {
            return this.sentByteCounter.get();
        }

        return this.sentByteCounter.addAndGet(delta);
    }

    protected long incrementSentPackets() {
        return this.sentPacketCounter.incrementAndGet();
    }

    protected long updatePeriodicRtcpSendInterval() {
        // TODO make this adaptative
        return (this.periodicRtcpSendInterval = 5);
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isRunning() {
        return this.running.get();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.host = host;
    }

    public boolean useNio() {
        return useNio;
    }

    public void setUseNio(boolean useNio) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.useNio = useNio;
    }

    public boolean isDiscardOutOfOrder() {
        return discardOutOfOrder;
    }

    public void setDiscardOutOfOrder(boolean discardOutOfOrder) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.discardOutOfOrder = discardOutOfOrder;
    }

    public int getBandwidthLimit() {
        return bandwidthLimit;
    }

    public void setBandwidthLimit(int bandwidthLimit) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.bandwidthLimit = bandwidthLimit;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.sendBufferSize = sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getMaxCollisionsBeforeConsideringLoop() {
        return maxCollisionsBeforeConsideringLoop;
    }

    public void setMaxCollisionsBeforeConsideringLoop(int maxCollisionsBeforeConsideringLoop) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.maxCollisionsBeforeConsideringLoop = maxCollisionsBeforeConsideringLoop;
    }

    public boolean isAutomatedRtcpHandling() {
        return automatedRtcpHandling;
    }

    public void setAutomatedRtcpHandling(boolean automatedRtcpHandling) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.automatedRtcpHandling = automatedRtcpHandling;
    }

    public boolean isTryToUpdateOnEverySdes() {
        return tryToUpdateOnEverySdes;
    }

    public void setTryToUpdateOnEverySdes(boolean tryToUpdateOnEverySdes) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.tryToUpdateOnEverySdes = tryToUpdateOnEverySdes;
    }

    public long getSentBytes() {
        return this.sentByteCounter.get();
    }

    public long getSentPackets() {
        return this.sentPacketCounter.get();
    }

    public int getParticipantDatabaseCleanup() {
        return participantDatabaseCleanup;
    }

    public void setParticipantDatabaseCleanup(int participantDatabaseCleanup) {
        if (this.running.get()) {
            throw new IllegalArgumentException("Cannot modify property after initialisation");
        }
        this.participantDatabaseCleanup = participantDatabaseCleanup;
    }
}
