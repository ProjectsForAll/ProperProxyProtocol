package gg.drak.pptw.mixin;

import gg.drak.pptw.netty.ProxyProtocolDecoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerConnectionListenerMixin extends ChannelInitializer<Channel> {
    @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At("HEAD"))
    private void onInitChannel(Channel channel, CallbackInfo ci) {
        channel.pipeline().addFirst(ProxyProtocolDecoder.NAME, new ProxyProtocolDecoder());
    }
}
