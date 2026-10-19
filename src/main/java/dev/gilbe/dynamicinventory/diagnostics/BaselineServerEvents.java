package dev.gilbe.dynamicinventory.diagnostics;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class BaselineServerEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BaselineDiagnostics.capture(event.player, "not available on logical server", "player-login", false);
    }
}
