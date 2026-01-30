package gg.drak.pptw.mixin;

import gg.drak.pptw.netty.ProxyProtocolDecoder;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "getRemoteAddress()Ljava/net/SocketAddress;", at = @At("HEAD"), cancellable = true)
    private void onGetRemoteAddress(CallbackInfoReturnable<SocketAddress> cir) {
        if (this.channel != null) {
            SocketAddress proxiedAddress = this.channel.attr(ProxyProtocolDecoder.PROXIED_ADDRESS).get();
            if (proxiedAddress != null) {
                cir.setReturnValue(proxiedAddress);
            }
        }
    }
}
