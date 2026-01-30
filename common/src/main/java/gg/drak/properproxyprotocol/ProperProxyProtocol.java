package gg.drak.properproxyprotocol;

import dev.architectury.event.events.common.LifecycleEvent;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;

/**
 * The main class for the Proxy Protocol That Works mod.
 */
public class ProperProxyProtocol {
    /**
     * The mod ID.
     */
    public static final String MOD_ID = "properproxyprotocol";

    /**
     * The Minecraft server instance.
     */
    @Getter @Setter
    private static MinecraftServer server;

    /**
     * Initializes the mod.
     */
    public static void init() {
        // Common initialization logic if any

//        LifecycleEvent.SERVER_BEFORE_START.register(ProxyProtocolThatWorks::setServer);
        LifecycleEvent.SERVER_STARTING.register(ProperProxyProtocol::setServer);
    }

    /**
     * Checks if the current thread is the same as the server thread.
     *
     * Note: If the server is not yet initialized, this will return true.
     * @return true if on the same thread, false otherwise
     */
    public static boolean isOnSameThread() {
        if (getServer() == null) return true;

        return getServer().isSameThread();
    }

    /**
     * Executes a runnable on the server thread.
     * @param runnable the runnable to execute
     */
    public static void execute(Runnable runnable) {
        if (getServer() == null) return;

        getServer().execute(runnable);
    }
}
