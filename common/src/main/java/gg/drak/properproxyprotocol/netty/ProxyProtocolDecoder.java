package gg.drak.properproxyprotocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Detects and decodes the HAProxy PROXY protocol (v1 and v2).
 * If detected, it adds the necessary Netty handlers to decode the protocol
 * and extract the real client IP address.
 */
public class ProxyProtocolDecoder extends BetterDecoder {
    /**
     * The name of this handler in the Netty pipeline.
     */
    public static final String NAME = "proxy_protocol_decoder";
    /**
     * Attribute key to store the proxied address in the channel.
     */
    public static final AttributeKey<SocketAddress> PROXIED_ADDRESS = AttributeKey.valueOf("pptw:proxied_address");

    /**
     * Reads incoming data to detect the PROXY protocol.
     * @param ctx the channel handler context
     * @param buf the incoming message
     * @throws Exception if an error occurs
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
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
            super.channelRead(ctx, buf);

            // Remove this detector as it's no longer needed
            ctx.pipeline().remove(this);
            return;
        } else {
            // Not PROXY protocol
            ctx.pipeline().remove(this);
        }
        super.channelRead(ctx, buf);
    }

    /**
     * Checks if the given buffer starts with a PROXY protocol header.
     * @param buf the buffer to check
     * @return true if it is a PROXY protocol header, false otherwise
     */
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

    /**
     * Checks if the given byte array matches the PROXY protocol v2 prefix.
     * @param bytes the byte array to check
     * @return true if it matches the v2 prefix, false otherwise
     */
    private boolean isV2Prefix(byte[] bytes) {
        return bytes[0] == 0x0D && bytes[1] == 0x0A && bytes[2] == 0x0D && bytes[3] == 0x0A &&
               bytes[4] == 0x00 && bytes[5] == 0x0D && bytes[6] == 0x0A && bytes[7] == 0x51 &&
               bytes[8] == 0x55 && bytes[9] == 0x49 && bytes[10] == 0x54 && bytes[11] == 0x0A;
    }

    /**
     * Handler to process the decoded HAProxyMessage and extract the real client address.
     */
    private static class HAProxyHandler extends ChannelInboundHandlerAdapter {
        /**
         * Handles the incoming HAProxyMessage.
         * @param ctx the channel handler context
         * @param msg the incoming message
         * @throws Exception if an error occurs
         */
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

        /**
         * Handles exceptions during processing.
         * @param ctx the channel handler context
         * @param cause the throwable cause
         * @throws Exception if an error occurs
         */
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
