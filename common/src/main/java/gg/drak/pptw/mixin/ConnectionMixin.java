package gg.drak.pptw.mixin;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "getRemoteAddress()Ljava/net/SocketAddress;", at = @At("HEAD"), cancellable = true)
    private void onGetRemoteAddress(CallbackInfoReturnable<SocketAddress> cir) {
        if (this.channel != null) {
            SocketAddress proxiedAddress = this.channel.attr(AttributeKey.<SocketAddress>valueOf("proxied_address")).get();
            if (proxiedAddress != null) {
                cir.setReturnValue(proxiedAddress);
            }
        }
    }
}