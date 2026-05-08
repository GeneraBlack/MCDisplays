package de.gener.mcdisplays.client;

import de.gener.mcdisplays.McDisplaysMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = McDisplaysMod.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class McDisplaysModClient {
    public McDisplaysModClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::registerMenuScreens);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(McDisplaysMod.DISPLAY_PANEL_BLOCK_ENTITY.get(), DisplayPanelBlockEntityRenderer::new);
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(McDisplaysMod.DISPLAY_PANEL_MENU.get(), DisplayPanelScreen::new);
    }
}