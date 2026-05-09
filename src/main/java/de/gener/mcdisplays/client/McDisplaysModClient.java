package de.gener.mcdisplays.client;

import de.gener.mcdisplays.McDisplaysMod;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class McDisplaysModClient {
    private McDisplaysModClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(McDisplaysModClient::registerRenderers);
        modEventBus.addListener(McDisplaysModClient::onClientSetup);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(McDisplaysMod.DISPLAY_PANEL_BLOCK_ENTITY.get(), DisplayPanelBlockEntityRenderer::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(McDisplaysMod.DISPLAY_PANEL_MENU.get(), DisplayPanelScreen::new));
    }
}