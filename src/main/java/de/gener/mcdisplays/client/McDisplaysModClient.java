package de.gener.mcdisplays.client;

import de.gener.mcdisplays.McDisplaysMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = McDisplaysMod.MODID, value = Dist.CLIENT)
public final class McDisplaysModClient {
    private McDisplaysModClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        de.gener.mcdisplays.markdown.TextMetrics.Holder.install(ClientTextMetrics.INSTANCE);
        event.registerBlockEntityRenderer(McDisplaysMod.DISPLAY_PANEL_BLOCK_ENTITY.get(), DisplayPanelBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(McDisplaysMod.DISPLAY_PANEL_MENU.get(), DisplayPanelScreen::new);
    }
}