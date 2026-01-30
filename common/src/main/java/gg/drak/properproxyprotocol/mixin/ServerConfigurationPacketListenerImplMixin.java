package gg.drak.properproxyprotocol.mixin;

import gg.drak.properproxyprotocol.ProperProxyProtocol;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {
    @Inject(method = "startNextTask()V", at = @At("HEAD"), cancellable = true)
    private void onStartNextTask(CallbackInfo ci) {
        if (ci.isCancelled()) return;

        // Force configuration tasks to start on the server thread.
        // This fixes a race condition where PrepareSpawnTask can run on a Netty thread
        // and crash the DistanceManager (it.unimi.dsi.fastutil rehash errors).
        if (! ProperProxyProtocol.isOnSameThread()) {
            ProperProxyProtocol.execute(this::startNextTask);
            ci.cancel();
        }
    }

    @Shadow
    protected abstract void startNextTask();
}
