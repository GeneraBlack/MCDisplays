package de.gener.mcdisplays.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.client.video.ClientVideoManager;
import de.gener.mcdisplays.client.video.IVideoPlayer;
import de.gener.mcdisplays.content.DisplayCluster;
import de.gener.mcdisplays.markdown.LayoutPage;
import de.gener.mcdisplays.markdown.MarkdownLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
public final class DisplayPanelBlockEntityRenderer implements BlockEntityRenderer<DisplayPanelBlockEntity> {
    private static final float FRAME_OUTER_INSET = 0.008F;
    private static final float FRAME_INNER_INSET = 0.040F;
    private static final float FRAME_DEPTH = 0.006F;
    private static final float PIXEL = 1.0F / MarkdownLayout.PX_PER_BLOCK;

    private final Font font;

    public DisplayPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(DisplayPanelBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        DisplayCluster.Cluster cluster = DisplayCluster.find(blockEntity.getLevel(), blockEntity.getBlockPos(), state);
        if (cluster.localX() != 0 || cluster.localY() != 0) {
            return;
        }

        LayoutPage page = blockEntity.getPage(cluster);
        boolean glowingDisplay = blockEntity.isGlowingDisplay() || blockEntity.isCinemaActive();
        int textLight = glowingDisplay ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getValue(DisplayPanelBlock.FACING).toYRot()));

        if (glowingDisplay) {
            renderGlowFrame(cluster, poseStack, bufferSource);
        }

        // Page pixels map 1:1 onto the display: the origin is the top-left corner, y grows downwards.
        poseStack.translate(-0.5D, cluster.height() - 0.5D, 0.508D);
        poseStack.scale(PIXEL, -PIXEL, PIXEL);

        VertexConsumer rects = null;
        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.RectOp rect) {
                if (rects == null) {
                    rects = bufferSource.getBuffer(RenderType.debugQuads());
                }
                drawRect(rects, poseStack, rect);
            }
        }

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.ImageOp img) {
                drawImage(img, poseStack, bufferSource, glowingDisplay, textLight);
            }
        }

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.VideoOp vid) {
                drawVideo(vid, cluster, state.getValue(DisplayPanelBlock.FACING), blockEntity, poseStack, bufferSource, glowingDisplay, textLight);
            }
        }

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.TextOp text) {
                drawText(text, poseStack, bufferSource, glowingDisplay, textLight);
            }
        }

        poseStack.popPose();
    }

    private void drawVideo(LayoutPage.VideoOp op, DisplayCluster.Cluster cluster, Direction facing, DisplayPanelBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, boolean glowing, int light) {
        Matrix4f pose = poseStack.last().pose();
        float x1 = op.x();
        float y1 = op.y();
        float x2 = x1 + op.width();
        float y2 = y1 + op.height();
        int videoLight = LightTexture.FULL_BRIGHT;

        if (op.blocked()) {
            drawPlaceholder(pose, bufferSource, x1, y1, x2, y2, "[Blocked] " + op.url(), 0x88FF5555, 0xFFFF6666, videoLight);
            return;
        }

        if (!ClientVideoManager.isWaterMediaAvailable()) {
            String label = op.title().isBlank() ? "Video Stream" : op.title();
            drawPlaceholder(pose, bufferSource, x1, y1, x2, y2, "[Video] " + label + " (Requires WATERMeDIA mod)", 0x888050D0, 0xFFC8B5EE, videoLight);
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        IVideoPlayer player = ClientVideoManager.getOrCreate(pos, op.url(), op.loop());
        if (player != null) {
            player.preRender();

            // Determine audio source origin (Projector block or Screen cluster)
            BlockPos projectorPos = blockEntity.getCinemaProjectorPos();
            boolean isProjector = !"screen".equalsIgnoreCase(op.audioSource()) && projectorPos != null;
            BlockPos audioPos = isProjector ? projectorPos : pos;

            // Register/update continuous spatial audio tracking in ClientVideoManager (prevents camera culling mute)
            ClientVideoManager.updateAudioSource(pos, audioPos, isProjector, cluster, facing, op.volume(), op.distance(), op.startEpoch());

            if (player.isReady()) {
                if (op.startEpoch() > 0L) {
                    player.syncPlaybackTime(op.startEpoch());
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double dist;
                    if (isProjector) {
                        dist = Math.sqrt(mc.player.distanceToSqr(
                            audioPos.getX() + 0.5D,
                            audioPos.getY() + 0.5D,
                            audioPos.getZ() + 0.5D
                        ));
                    } else {
                        dist = ClientVideoManager.getDistanceToCluster(mc.player.position(), pos, cluster.width(), cluster.height(), facing);
                    }
                    player.tickAudio(dist, op.volume(), op.distance());
                }

                ResourceLocation location = player.getLocation();
                if (location != null) {
                    VertexConsumer consumer = bufferSource.getBuffer(RenderType.text(location));
                    int alpha = 255;
                    consumer.addVertex(pose, x1, y2, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 1.0F).setLight(videoLight);
                    consumer.addVertex(pose, x2, y2, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 1.0F).setLight(videoLight);
                    consumer.addVertex(pose, x2, y1, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 0.0F).setLight(videoLight);
                    consumer.addVertex(pose, x1, y1, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 0.0F).setLight(videoLight);
                    return;
                }
            }
        }

        if (player != null && player.isBroken()) {
            String header = "[CINEMA FEHLER] Wiedergabe fehlgeschlagen";
            drawVideoPlaceholder(poseStack, bufferSource, x1, y1, x2, y2, header, op.url(), player, true, videoLight);
        } else {
            String stateDesc = (player != null && player.isBuffering()) ? "Puffert..." : "Lade Stream...";
            String header = "[CINEMA] " + (op.title().isBlank() ? stateDesc : op.title() + " (" + stateDesc + ")");
            drawVideoPlaceholder(poseStack, bufferSource, x1, y1, x2, y2, header, op.url(), player, false, videoLight);
        }
    }

    private void drawVideoPlaceholder(PoseStack poseStack, MultiBufferSource bufferSource, float x1, float y1, float x2, float y2,
                                     String header, String url, @Nullable IVideoPlayer player, boolean broken, int light) {
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer rects = bufferSource.getBuffer(RenderType.debugQuads());
        drawRectDirect(rects, pose, x1, y1, x2, y2, 0xFF0D0F14);

        int borderColor = broken ? 0xAAFF5555 : 0xAA80DEEA;
        int headerColor = broken ? 0xFFFF6666 : 0xFF80DEEA;
        drawRectDirect(rects, pose, x1, y1, x2, y1 + 1.0F, borderColor);
        drawRectDirect(rects, pose, x1, y2 - 1.0F, x2, y2, borderColor);
        drawRectDirect(rects, pose, x1, y1, x1 + 1.0F, y2, borderColor);
        drawRectDirect(rects, pose, x2 - 1.0F, y1, x2, y2, borderColor);

        float boxWidth = x2 - x1;
        float boxHeight = y2 - y1;
        float maxTextW = Math.max(8.0F, boxWidth - 12.0F);

        List<String> lines = new ArrayList<>();
        lines.add(truncate(header, maxTextW));
        if (player != null) {
            String reason = player.getFailureReason();
            if (broken && !reason.isEmpty()) {
                lines.add(truncate(reason, maxTextW));
            } else {
                String stateStr = player.getStateName();
                long timeMs = player.getTime();
                long durMs = player.getDuration();
                lines.add(truncate(String.format("Status: %s | Buffer: %b | %d/%d ms",
                    stateStr, player.isBuffering(), timeMs, durMs), maxTextW));
            }
        } else {
            lines.add(truncate("Initialisiere Video-Engine...", maxTextW));
        }

        if (url != null && !url.isBlank()) {
            lines.add(truncate("URL: " + url, maxTextW));
        }

        if (broken) {
            lines.add(truncate("Format: Direct MP4/WebM/HLS (m3u8), Twitch, Kick", maxTextW));
            lines.add(truncate("Einstellungen am Kino-Projektor anpassen", maxTextW));
        }

        int maxLines = Math.max(1, (int) (boxHeight / (font.lineHeight + 3.0F)));
        while (lines.size() > maxLines) {
            lines.remove(lines.size() - 1);
        }

        float totalHeight = lines.size() * (font.lineHeight + 3.0F) - 3.0F;
        float startY = y1 + Math.max(1.0F, (boxHeight - totalHeight) / 2.0F);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.05F);
        Matrix4f textPose = poseStack.last().pose();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float textW = font.width(line);
            float tx = x1 + Math.max(2.0F, (boxWidth - textW) / 2.0F);
            float ty = startY + i * (font.lineHeight + 3.0F);
            int color = (i == 0) ? headerColor : (broken ? 0xFFFFAAAA : 0xFFB6A7D3);
            font.drawInBatch(Component.literal(line), tx, ty, color, false, textPose, bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
        poseStack.popPose();
    }

    private String truncate(String text, float maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String s = text;
        while (s.length() > 4 && font.width(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    private void drawImage(LayoutPage.ImageOp op, PoseStack poseStack, MultiBufferSource bufferSource, boolean glowing, int light) {
        Matrix4f pose = poseStack.last().pose();
        float x1 = op.x();
        float y1 = op.y();
        float x2 = x1 + op.width();
        float y2 = y1 + op.height();

        if (op.blocked()) {
            drawPlaceholder(pose, bufferSource, x1, y1, x2, y2, "Media blocked", 0x88FF5555, 0xFFFF6666, light);
            return;
        }

        ClientImageManager.ImageInfo info = ClientImageManager.getImage(op.url());
        if (info != null) {
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.text(info.location()));
            int alpha = glowing ? 255 : 240;
            consumer.addVertex(pose, x1, y2, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 1.0F).setLight(light);
            consumer.addVertex(pose, x2, y2, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 1.0F).setLight(light);
            consumer.addVertex(pose, x2, y1, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 0.0F).setLight(light);
            consumer.addVertex(pose, x1, y1, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 0.0F).setLight(light);
            return;
        }

        ClientImageManager.Status status = ClientImageManager.getStatus(op.url());
        if (status == ClientImageManager.Status.FAILED) {
            String label = op.alt().isBlank() ? "Failed to load image" : op.alt();
            drawPlaceholder(pose, bufferSource, x1, y1, x2, y2, label, 0x66FF5555, 0xFFFF6666, light);
        } else {
            String label = op.alt().isBlank() ? "Loading image..." : op.alt();
            drawPlaceholder(pose, bufferSource, x1, y1, x2, y2, label, 0x6688B6A7, 0xFFB6A7D3, light);
        }
    }

    private void drawPlaceholder(Matrix4f pose, MultiBufferSource bufferSource, float x1, float y1, float x2, float y2, String text, int borderColor, int textColor, int light) {
        VertexConsumer rects = bufferSource.getBuffer(RenderType.debugQuads());
        drawRectDirect(rects, pose, x1, y1, x2, y2, 0xEE111418);
        drawRectDirect(rects, pose, x1, y1, x2, y1 + 1.0F, borderColor);
        drawRectDirect(rects, pose, x1, y2 - 1.0F, x2, y2, borderColor);
        drawRectDirect(rects, pose, x1, y1, x1 + 1.0F, y2, borderColor);
        drawRectDirect(rects, pose, x2 - 1.0F, y1, x2, y2, borderColor);

        float maxW = Math.max(8.0F, x2 - x1 - 4.0F);
        String displayText = truncate(text, maxW);
        float textW = font.width(displayText);
        float tx = x1 + Math.max(0.0F, (x2 - x1 - textW) / 2.0F);
        float ty = y1 + Math.max(0.0F, (y2 - y1 - font.lineHeight) / 2.0F);
        font.drawInBatch(Component.literal(displayText), tx, ty, textColor, false, pose, bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

    private static void drawRectDirect(VertexConsumer consumer, Matrix4f pose, float x1, float y1, float x2, float y2, int color) {
        consumer.addVertex(pose, x1, y2, 0.0F).setColor(color);
        consumer.addVertex(pose, x2, y2, 0.0F).setColor(color);
        consumer.addVertex(pose, x2, y1, 0.0F).setColor(color);
        consumer.addVertex(pose, x1, y1, 0.0F).setColor(color);
    }

    private static void drawRect(VertexConsumer consumer, PoseStack poseStack, LayoutPage.RectOp rect) {
        Matrix4f pose = poseStack.last().pose();
        float x1 = rect.x();
        float y1 = rect.y();
        float x2 = x1 + rect.width();
        float y2 = y1 + rect.height();
        int color = rect.argb();
        consumer.addVertex(pose, x1, y2, 0.0F).setColor(color);
        consumer.addVertex(pose, x2, y2, 0.0F).setColor(color);
        consumer.addVertex(pose, x2, y1, 0.0F).setColor(color);
        consumer.addVertex(pose, x1, y1, 0.0F).setColor(color);
    }

    private void drawText(LayoutPage.TextOp op, PoseStack poseStack, MultiBufferSource bufferSource, boolean glowing, int light) {
        ClientTextMetrics.Prepared prepared = ClientTextMetrics.prepare(op);
        poseStack.pushPose();
        poseStack.translate(op.x, op.y, 0.0F);
        poseStack.scale(op.scale, op.scale, 1.0F);
        if (glowing) {
            font.drawInBatch8xOutline(prepared.sequence(), 0.0F, 0.0F, op.color | 0xFF000000, outlineColor(op.color), poseStack.last().pose(), bufferSource, light);
        } else {
            font.drawInBatch(prepared.component(), 0.0F, 0.0F, op.color | 0xFF000000, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
        poseStack.popPose();
    }
    private void renderGlowFrame(DisplayCluster.Cluster cluster, PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        float maxX = cluster.width() - 0.5F;
        float maxY = cluster.height() - 0.5F;
        float zMin = 0.5F + FRAME_DEPTH;
        float zMax = zMin + 0.002F;

        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            -0.5F + FRAME_OUTER_INSET,
            -0.5F + FRAME_OUTER_INSET,
            zMin,
            maxX - FRAME_OUTER_INSET,
            maxY - FRAME_OUTER_INSET,
            zMax,
            0.97F,
            0.91F,
            0.53F,
            1.0F
        );
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            -0.5F + FRAME_INNER_INSET,
            -0.5F + FRAME_INNER_INSET,
            zMin,
            maxX - FRAME_INNER_INSET,
            maxY - FRAME_INNER_INSET,
            zMax,
            0.47F,
            0.90F,
            1.0F,
            0.90F
        );
    }

    private static int outlineColor(int baseColor) {
        int red = (baseColor >> 16) & 0xFF;
        int green = (baseColor >> 8) & 0xFF;
        int blue = baseColor & 0xFF;
        red = Math.max(12, red / 5);
        green = Math.max(12, green / 5);
        blue = Math.max(12, blue / 5);
        return (red << 16) | (green << 8) | blue;
    }

    @Override
    public boolean shouldRenderOffScreen(DisplayPanelBlockEntity blockEntity) {
        return true;
    }
}