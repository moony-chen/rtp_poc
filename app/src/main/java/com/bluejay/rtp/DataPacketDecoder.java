
package com.bluejay.rtp;

import com.biasedbit.efflux.logging.Logger;
import com.biasedbit.efflux.packet.DataPacket;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;


public class DataPacketDecoder extends MessageToMessageDecoder<ByteBuf> {

    // constants ------------------------------------------------------------------------------------------------------

    protected static final Logger LOG = Logger.getLogger(MessageToMessageDecoder.class);

    // OneToOneDecoder ------------------------------------------------------------------------------------------------


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        try {
            out.add( DataPacket.decode(msg));
        } catch (Exception e) {
            LOG.debug("Failed to decode RTP packet.", e);
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return true;
    }
}
