package com.fakeplayer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(FakePlayerMod.MOD_ID)
public final class FakePlayerMod {
    public static final String MOD_ID = "fakeplayer";

    public FakePlayerMod(IEventBus modBus) {
        FakePlayerCommands commands = new FakePlayerCommands();
        NeoForge.EVENT_BUS.register(commands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarted(ServerStartedEvent event) {
        FakePlayerManager.init(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        FakePlayerManager.saveAndClear(event.getServer());
    }
}
