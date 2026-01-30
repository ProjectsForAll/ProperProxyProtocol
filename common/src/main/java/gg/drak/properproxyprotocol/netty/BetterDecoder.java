package gg.drak.properproxyprotocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * A better decoder that provides a more specific channelRead method for ByteBuf.
 */
public class BetterDecoder extends ChannelInboundHandlerAdapter {
    /**
     * Reads incoming messages and delegates to the appropriate method.
     * @param ctx the channel handler context
     * @param msg the incoming message
     * @throws Exception if an error occurs
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            channelRead(ctx, buf);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    /**
     * Handles incoming ByteBuf messages.
     * @param ctx the channel handler context
     * @param buf the incoming ByteBuf message
     * @throws Exception if an error occurs
     */
    public void channelRead(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        super.channelRead(ctx, (Object) buf);
    }
}
