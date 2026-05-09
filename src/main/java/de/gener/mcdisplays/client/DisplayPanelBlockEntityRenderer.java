package de.gener.mcdisplays.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.content.DisplayCluster;
import de.gener.mcdisplays.content.DisplayRichText;
import java.util.Objects;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class DisplayPanelBlockEntityRenderer implements BlockEntityRenderer<DisplayPanelBlockEntity> {
    private static final float FRAME_OUTER_INSET = 0.008F;
    private static final float FRAME_INNER_INSET = 0.040F;
    private static final float FRAME_DEPTH = 0.006F;
    private static final float TEXT_SCALE_MIN = 0.0070F;
    private static final float TEXT_SCALE_MAX = 0.0125F;
    private static final float TEXT_HORIZONTAL_MARGIN = 0.0625F;
    private static final float TEXT_VERTICAL_MARGIN = 0.0625F;
    private static final ResourceLocation UNIFORM_FONT = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:uniform"));

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

        List<DisplayRichText.StyledLine> lines = blockEntity.getPage(cluster).lines();
        boolean glowingDisplay = blockEntity.isGlowingDisplay();
        int textLight = glowingDisplay ? LightTexture.FULL_BRIGHT : packedLight;
        List<RenderedLine> renderedLines = buildRenderedLines(lines);
        TextLayoutMetrics textLayout = computeTextLayout(cluster, renderedLines);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getValue(DisplayPanelBlock.FACING).toYRot()));

        if (glowingDisplay) {
            renderGlowFrame(cluster, poseStack, bufferSource);
        }

        poseStack.translate(textLayout.originX(), textLayout.originY(), 0.501D);
        poseStack.scale(textLayout.scale(), -textLayout.scale(), textLayout.scale());

        float lineStep = font.lineHeight + 1.0F;
        for (int index = 0; index < renderedLines.size(); index++) {
            RenderedLine renderedLine = renderedLines.get(index);
            DisplayRichText.StyledLine line = renderedLine.line();
            if (line.isBlank()) {
                continue;
            }

            float y = index * lineStep;
            if (glowingDisplay) {
                font.drawInBatch8xOutline(renderedLine.visualOrderText(), 0.0F, y, renderedLine.color(), outlineColor(renderedLine.color()), poseStack.last().pose(), bufferSource, textLight);
            } else {
                font.drawInBatch(renderedLine.component(), 0.0F, y, renderedLine.color(), false, poseStack.last().pose(), bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, textLight);
            }
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

    private List<RenderedLine> buildRenderedLines(List<DisplayRichText.StyledLine> lines) {
        List<RenderedLine> renderedLines = new java.util.ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            int color = 0xF3F3F3;
            if (index == 0) {
                color = 0xF6D58B;
            } else if (index == lines.size() - 1) {
                color = 0x9CB5C8;
            }

            MutableComponent component = lines.get(index).toComponent(color, UNIFORM_FONT);
            FormattedCharSequence visualOrderText = component.getVisualOrderText();
            renderedLines.add(new RenderedLine(lines.get(index), component, visualOrderText, color, font.width(visualOrderText)));
        }
        return List.copyOf(renderedLines);
    }

    private TextLayoutMetrics computeTextLayout(DisplayCluster.Cluster cluster, List<RenderedLine> renderedLines) {
        int widestLinePixels = renderedLines.stream().mapToInt(RenderedLine::pixelWidth).max().orElse(1);
        float lineStepPixels = font.lineHeight + 1.0F;
        float contentHeightPixels = renderedLines.isEmpty() ? font.lineHeight : font.lineHeight + Math.max(0, renderedLines.size() - 1) * lineStepPixels;
        float availableWidth = Math.max(0.1F, cluster.width() - TEXT_HORIZONTAL_MARGIN * 2.0F);
        float availableHeight = Math.max(0.1F, cluster.height() - TEXT_VERTICAL_MARGIN * 2.0F);
        float scaleByWidth = availableWidth / Math.max(1, widestLinePixels);
        float scaleByHeight = availableHeight / Math.max(1.0F, contentHeightPixels);
        float scale = Mth.clamp(Math.min(scaleByWidth, scaleByHeight), TEXT_SCALE_MIN, TEXT_SCALE_MAX);

        float contentWidthWorld = widestLinePixels * scale;
        float contentHeightWorld = contentHeightPixels * scale;
        float horizontalPadding = Math.max(0.0F, (availableWidth - contentWidthWorld) * 0.5F);
        float verticalPadding = Math.max(0.0F, (availableHeight - contentHeightWorld) * 0.5F);
        float originX = -0.5F + TEXT_HORIZONTAL_MARGIN + horizontalPadding;
        float originY = cluster.height() - 0.5F - TEXT_VERTICAL_MARGIN - verticalPadding;
        return new TextLayoutMetrics(originX, originY, scale);
    }

    private record RenderedLine(DisplayRichText.StyledLine line, MutableComponent component, FormattedCharSequence visualOrderText, int color, int pixelWidth) {
    }

    private record TextLayoutMetrics(float originX, float originY, float scale) {
    }

    @Override
    public boolean shouldRenderOffScreen(DisplayPanelBlockEntity blockEntity) {
        return true;
    }
}