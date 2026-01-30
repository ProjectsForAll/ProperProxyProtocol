package gg.drak.pptw.fabric;

import gg.drak.pptw.ProxyProtocolThatWorks;
import net.fabricmc.api.ModInitializer;

public class ProxyProtocolThatWorksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ProxyProtocolThatWorks.init();
    }
}
