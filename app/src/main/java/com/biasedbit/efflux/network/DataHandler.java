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
import com.biasedbit.efflux.packet.DataPacket;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class DataHandler extends SimpleChannelInboundHandler<DataPacket> {

    // constants ------------------------------------------------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(DataHandler.class);

    // internal vars --------------------------------------------------------------------------------------------------

    private final AtomicInteger counter;
    private final DataPacketReceiver receiver;

    // constructors ---------------------------------------------------------------------------------------------------

    public DataHandler(DataPacketReceiver receiver) {
        this.receiver = receiver;
        this.counter = new AtomicInteger();
    }

    // SimpleChannelUpstreamHandler -----------------------------------------------------------------------------------


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DataPacket msg) throws Exception {
        this.receiver.dataPacketReceived(ctx.channel().remoteAddress(), msg);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("Caught exception on channel {}.", cause, ctx.channel());
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return super.acceptInboundMessage(msg);
    }

    // public methods -------------------------------------------------------------------------------------------------

    public int getPacketsReceived() {
        return this.counter.get();
    }
}
