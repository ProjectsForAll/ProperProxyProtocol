package gg.drak.pptw.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Detects PROXY protocol and updates the channel's remote address.
 * Inspired by PaperMC's implementation.
 */
public class ProxyProtocolDecoder extends ChannelInboundHandlerAdapter {
    public static final String NAME = "proxy_protocol_decoder";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            if (isProxyProtocol(buf)) {
                ctx.pipeline().addAfter(NAME, "haproxy_decoder", new HAProxyMessageDecoder());
                ctx.pipeline().addAfter("haproxy_decoder", "haproxy_handler", new HAProxyHandler());
                ctx.pipeline().remove(this);
                super.channelRead(ctx, msg);
                return;
            } else {
                // Not PROXY protocol, just remove ourselves and continue
                ctx.pipeline().remove(this);
            }
        }
        super.channelRead(ctx, msg);
    }

    private boolean isProxyProtocol(ByteBuf buf) {
        if (buf.readableBytes() < 8) return false;
        
        int readerIndex = buf.readerIndex();
        try {
            // Check for V2 prefix
            if (buf.readableBytes() >= 12) {
                byte[] v2Prefix = new byte[12];
                buf.getBytes(readerIndex, v2Prefix);
                if (isV2Prefix(v2Prefix)) return true;
            }
            
            // Check for V1 prefix "PROXY "
            if (buf.readableBytes() >= 6) {
                byte[] v1Prefix = new byte[6];
                buf.getBytes(readerIndex, v1Prefix);
                if (new String(v1Prefix).equals("PROXY ")) return true;
            }
        } catch (Exception ignored) {}
        
        return false;
    }

    private boolean isV2Prefix(byte[] bytes) {
        return bytes[0] == 0x0D && bytes[1] == 0x0A && bytes[2] == 0x0D && bytes[3] == 0x0A &&
               bytes[4] == 0x00 && bytes[5] == 0x0D && bytes[6] == 0x0A && bytes[7] == 0x51 &&
               bytes[8] == 0x55 && bytes[9] == 0x49 && bytes[10] == 0x54 && bytes[11] == 0x0A;
    }

    private static class HAProxyHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HAProxyMessage haproxyMsg) {
                try {
                    String sourceAddress = haproxyMsg.sourceAddress();
                    int sourcePort = haproxyMsg.sourcePort();
                    
                    if (sourceAddress != null) {
                        InetSocketAddress realAddress = new InetSocketAddress(sourceAddress, sourcePort);
                        // In Netty, we can't easily change the remoteAddress() of the channel itself as it's often fixed.
                        // However, Minecraft's Connection object often uses the channel's remoteAddress.
                        // We might need to wrap the channel or use an attribute that the Mixin can read.
                        ctx.channel().attr(AttributeKey.<SocketAddress>valueOf("proxied_address")).set(realAddress);
                    }
                } finally {
                    haproxyMsg.release();
                    ctx.pipeline().remove(this);
                    ctx.pipeline().remove("haproxy_decoder");
                }
            } else {
                super.channelRead(ctx, msg);
            }
        }
    }
}
