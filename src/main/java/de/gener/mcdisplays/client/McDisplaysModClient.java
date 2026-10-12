package de.gener.mcdisplays.client;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.client.video.ClientVideoManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = McDisplaysMod.MODID, value = Dist.CLIENT)
public final class McDisplaysModClient {
    private static boolean gameEventsRegistered = false;

    private McDisplaysModClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        de.gener.mcdisplays.markdown.TextMetrics.Holder.install(ClientTextMetrics.INSTANCE);
        event.registerBlockEntityRenderer(McDisplaysMod.DISPLAY_PANEL_BLOCK_ENTITY.get(), DisplayPanelBlockEntityRenderer::new);
        registerGameEvents();
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(McDisplaysMod.DISPLAY_PANEL_MENU.get(), DisplayPanelScreen::new);
    }

    private static synchronized void registerGameEvents() {
        if (gameEventsRegistered) {
            return;
        }
        gameEventsRegistered = true;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClientVideoManager.tickClient());
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                ClientVideoManager.releaseAll();
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientVideoManager.releaseAll());
    }
}