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

package com.biasedbit.efflux.network;

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.packet.CompoundControlPacket;
import com.biasedbit.efflux.packet.ControlPacket;
import java.util.List;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
@ChannelHandler.Sharable
public class ControlPacketEncoder extends ChannelOutboundHandlerAdapter {

    // constants ------------------------------------------------------------------------------------------------------

    protected static final Logger LOG = Logger.getLogger(ControlPacketEncoder.class);

    // constructors ---------------------------------------------------------------------------------------------------

    private ControlPacketEncoder() {
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static ControlPacketEncoder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    // ChannelDownstreamHandler ---------------------------------------------------------------------------------------


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        try {
            if (e.getMessage() instanceof ControlPacket) {
                Channels.write(ctx, e.getFuture(), ((ControlPacket) e.getMessage()).encode(), e.getRemoteAddress());
            } else if (e.getMessage() instanceof CompoundControlPacket) {
                List<ControlPacket> packets = ((CompoundControlPacket) e.getMessage()).getControlPackets();
                ChannelBuffer[] buffers = new ChannelBuffer[packets.size()];
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = packets.get(i).encode();
                }
                ChannelBuffer compoundBuffer = ChannelBuffers.wrappedBuffer(buffers);
                Channels.write(ctx, e.getFuture(), compoundBuffer, e.getRemoteAddress());
            }
        } catch (Exception e1) {
            LOG.error("Failed to encode compound RTCP packet to send.", e1);
        }

        // Otherwise do nothing.
    }

    // private classes ------------------------------------------------------------------------------------------------

    private static final class InstanceHolder {
        private static final ControlPacketEncoder INSTANCE = new ControlPacketEncoder();
    }
}
