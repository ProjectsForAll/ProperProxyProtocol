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
    public static final AttributeKey<SocketAddress> PROXIED_ADDRESS = AttributeKey.valueOf("pptw:proxied_address");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            if (buf.readableBytes() < 6) {
                if (buf.readableBytes() > 0) {
                    byte first = buf.getByte(buf.readerIndex());
                    if (first != 'P' && first != 0x0D) {
                        ctx.pipeline().remove(this);
                    } else {
                        return; // Wait for more bytes
                    }
                } else {
                    return; // Empty buffer
                }
            } else if (isProxyProtocol(buf)) {
                // Add HAProxy handlers. We use a temporary name for the decoder.
                ctx.pipeline().addAfter(ctx.name(), "haproxy_decoder", new HAProxyMessageDecoder());
                ctx.pipeline().addAfter("haproxy_decoder", "haproxy_handler", new HAProxyHandler());
                
                // Pass the buffer to the next handler (haproxy_decoder)
                super.channelRead(ctx, msg);
                
                // Remove this detector as it's no longer needed
                ctx.pipeline().remove(this);
                return;
            } else {
                // Not PROXY protocol
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
                        ctx.channel().attr(PROXIED_ADDRESS).set(realAddress);
                    }
                } finally {
                    haproxyMsg.release();
                    // Remove this handler immediately
                    ctx.pipeline().remove(this);
                    // Safely remove the decoder in the next event loop tick
                    ctx.executor().execute(() -> {
                        try {
                            if (ctx.pipeline().get("haproxy_decoder") != null) {
                                ctx.pipeline().remove("haproxy_decoder");
                            }
                        } catch (Exception ignored) {}
                    });
                }
            } else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            // If the PROXY header is malformed, just remove the handlers and continue
            // or let the exception propagate to disconnect the client.
            // For security, it's better to disconnect.
            try {
                ctx.pipeline().remove(this);
                if (ctx.pipeline().get("haproxy_decoder") != null) {
                    ctx.pipeline().remove("haproxy_decoder");
                }
            } catch (Exception ignored) {}
            super.exceptionCaught(ctx, cause);
        }
    }
}
