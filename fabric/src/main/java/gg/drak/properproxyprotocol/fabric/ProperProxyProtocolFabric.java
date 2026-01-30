package gg.drak.properproxyprotocol.fabric;

import gg.drak.properproxyprotocol.ProperProxyProtocol;
import net.fabricmc.api.ModInitializer;

public class ProperProxyProtocolFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ProperProxyProtocol.init();
    }
}
