package de.gener.mcdisplays.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.content.DisplayCluster;
import de.gener.mcdisplays.markdown.LayoutPage;
import de.gener.mcdisplays.markdown.MarkdownLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
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
        boolean glowingDisplay = blockEntity.isGlowingDisplay();
        int textLight = glowingDisplay ? LightTexture.FULL_BRIGHT : packedLight;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getValue(DisplayPanelBlock.FACING).toYRot()));

        if (glowingDisplay) {
            renderGlowFrame(cluster, poseStack, bufferSource);
        }

        // Page pixels map 1:1 onto the display: the origin is the top-left corner, y grows downwards.
        poseStack.translate(-0.5D, cluster.height() - 0.5D, 0.501D);
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
            if (op instanceof LayoutPage.TextOp text) {
                drawText(text, poseStack, bufferSource, glowingDisplay, textLight);
            }
        }

        poseStack.popPose();
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
        drawRectDirect(rects, pose, x1, y1, x2, y1 + 1.0F, borderColor);
        drawRectDirect(rects, pose, x1, y2 - 1.0F, x2, y2, borderColor);
        drawRectDirect(rects, pose, x1, y1, x1 + 1.0F, y2, borderColor);
        drawRectDirect(rects, pose, x2 - 1.0F, y1, x2, y2, borderColor);

        float textW = font.width(text);
        float tx = x1 + Math.max(0.0F, (x2 - x1 - textW) / 2.0F);
        float ty = y1 + Math.max(0.0F, (y2 - y1 - font.lineHeight) / 2.0F);
        font.drawInBatch(net.minecraft.network.chat.Component.literal(text), tx, ty, textColor, false, pose, bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, light);
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