package gg.drak.pptw;

import dev.architectury.event.events.common.LifecycleEvent;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;

public class ProxyProtocolThatWorks {
    public static final String MOD_ID = "proxyprotocolthatworks";

    @Getter @Setter
    private static MinecraftServer server;

    public static void init() {
        // Common initialization logic if any

//        LifecycleEvent.SERVER_BEFORE_START.register(ProxyProtocolThatWorks::setServer);
        LifecycleEvent.SERVER_STARTING.register(ProxyProtocolThatWorks::setServer);
    }

    public static boolean isOnSameThread() {
        if (getServer() == null) return true;

        return getServer().isSameThread();
    }

    public static void execute(Runnable runnable) {
        if (getServer() == null) return;

        getServer().execute(runnable);
    }
}
