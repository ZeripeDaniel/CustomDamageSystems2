package org.zeripe.angongui.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.zeripe.angongui.client.network.NetworkHandler;

public class CustomDamageSystemClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NetworkHandler.register();
        InputHandler.register();
        CombatQuickslotRenderer.register();
        DamageNumberRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(LocalStatManager::tick);
    }
}
