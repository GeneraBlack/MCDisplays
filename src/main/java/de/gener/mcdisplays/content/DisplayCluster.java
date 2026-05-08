package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysConfig;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public final class DisplayCluster {
    private DisplayCluster() {
    }

    public static Cluster find(LevelReader level, BlockPos origin, BlockState state) {
        if (!(state.getBlock() instanceof DisplayPanelBlock) || !state.hasProperty(DisplayPanelBlock.FACING)) {
            return new Cluster(List.of(origin.immutable()), Direction.NORTH, 1, 1, 0, 0);
        }

        Direction facing = state.getValue(DisplayPanelBlock.FACING);
        List<BlockPos> members = collectMembers(level, origin, facing);
        int minY = members.stream().mapToInt(BlockPos::getY).min().orElse(origin.getY());
        int maxY = members.stream().mapToInt(BlockPos::getY).max().orElse(origin.getY());
        int minHorizontal = members.stream().mapToInt(pos -> horizontalCoordinate(pos, facing)).min().orElse(horizontalCoordinate(origin, facing));
        int maxHorizontal = members.stream().mapToInt(pos -> horizontalCoordinate(pos, facing)).max().orElse(horizontalCoordinate(origin, facing));
        int width = maxHorizontal - minHorizontal + 1;
        int height = maxY - minY + 1;

        if (width > McDisplaysConfig.maxDisplayWidth() || height > McDisplaysConfig.maxDisplayHeight()) {
            return single(origin, facing);
        }

        Set<String> occupied = new HashSet<>();
        for (BlockPos pos : members) {
            occupied.add(horizontalCoordinate(pos, facing) + ":" + pos.getY());
        }

        if (members.size() != width * height) {
            return single(origin, facing);
        }

        for (int y = minY; y <= maxY; y++) {
            for (int horizontal = minHorizontal; horizontal <= maxHorizontal; horizontal++) {
                if (!occupied.contains(horizontal + ":" + y)) {
                    return single(origin, facing);
                }
            }
        }

        members.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).thenComparingInt(pos -> horizontalCoordinate(pos, facing)));
        int localX = horizontalCoordinate(origin, facing) - minHorizontal;
        int localY = origin.getY() - minY;
        return new Cluster(List.copyOf(members), facing, width, height, localX, localY);
    }

    private static Cluster single(BlockPos origin, Direction facing) {
        return new Cluster(List.of(origin.immutable()), facing, 1, 1, 0, 0);
    }

    private static List<BlockPos> collectMembers(LevelReader level, BlockPos origin, Direction facing) {
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        Direction screenRight = facing.getClockWise();
        Direction screenLeft = facing.getCounterClockWise();

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current.immutable())) {
                continue;
            }

            BlockState currentState = level.getBlockState(current);
            if (!(currentState.getBlock() instanceof DisplayPanelBlock) || !currentState.hasProperty(DisplayPanelBlock.FACING) || currentState.getValue(DisplayPanelBlock.FACING) != facing) {
                continue;
            }

            members.add(current.immutable());
            queue.add(current.relative(Direction.UP));
            queue.add(current.relative(Direction.DOWN));
            queue.add(current.relative(screenLeft));
            queue.add(current.relative(screenRight));
        }

        return members;
    }

    private static int horizontalCoordinate(BlockPos pos, Direction facing) {
        Direction screenRight = facing.getCounterClockWise();
        return pos.getX() * screenRight.getStepX() + pos.getZ() * screenRight.getStepZ();
    }

    public record Cluster(List<BlockPos> members, Direction facing, int width, int height, int localX, int localY) {
    }
}