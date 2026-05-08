package de.gener.mcdisplays.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.content.DisplayCluster;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class DisplayPanelBlockEntityRenderer implements BlockEntityRenderer<DisplayPanelBlockEntity> {
    private static final float TEXT_SCALE = 0.0080F;
    private static final ResourceLocation UNIFORM_FONT = ResourceLocation.parse("minecraft:uniform");

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

        List<String> lines = blockEntity.getPage(cluster).lines();

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getValue(DisplayPanelBlock.FACING).toYRot()));
        poseStack.translate(-0.4375D, 0.4375D + (cluster.height() - 1), 0.501D);
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        float lineStep = (float) font.lineHeight + 1.0F;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }

            int color = 0xF3F3F3;
            if (index == 0) {
                color = 0xF6D58B;
            } else if (index == lines.size() - 1) {
                color = 0x9CB5C8;
            }
            Component component = Component.literal(line).withStyle(style -> style.withFont(UNIFORM_FONT));
            font.drawInBatch(component, 0.0F, index * lineStep, color, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DisplayPanelBlockEntity blockEntity) {
        return true;
    }
}