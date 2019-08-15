
package com.bluejay.rtp;


public class DataPacketEncoder  {

    // constructors ---------------------------------------------------------------------------------------------------

    private DataPacketEncoder() {
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static DataPacketEncoder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    // OneToOneEncoder ------------------------------------------------------------------------------------------------


    protected byte[] encode(DataPacket packet) throws Exception {

        if (packet.getDataSize() == 0) {
            return new byte[0];
        }
        return packet.encode();
    }


    // private classes ------------------------------------------------------------------------------------------------

    private static final class InstanceHolder {
        private static final DataPacketEncoder INSTANCE = new DataPacketEncoder();
    }
}
