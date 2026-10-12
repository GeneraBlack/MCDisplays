package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysConfig;
import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.Ae2CpuMonitorItem;
import de.gener.mcdisplays.item.Ae2CpuMonitorItem.Ae2MonitorData;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Reads the crafting CPUs of a linked Applied Energistics 2 network and formats them for display.
 * <p>
 * All AE2 access goes through reflection on the public AE2 API interfaces, so AE2 stays an
 * optional dependency. If the linked chunk is not loaded, the last known state (stored in the
 * item) is shown together with its timestamp.
 */
public final class Ae2CpuMonitorExtractor {
    private static final String TITLE = "AE2 Crafting CPUs";
    private static final int LINES_PER_PAGE = 10;
    private static final long CACHE_REFRESH_MILLIS = 30_000L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss").withZone(ZoneId.systemDefault());

    private Ae2CpuMonitorExtractor() {
    }

    public static boolean isAe2Loaded() {
        return ModList.get().isLoaded("ae2");
    }

    /**
     * @return number of crafting CPUs of the network at the position, or -1 if there is no AE2 network.
     */
    public static int countCpus(Level level, BlockPos pos) {
        try {
            Object grid = resolveGrid(level, pos);
            if (grid == null) {
                return -1;
            }
            return cpus(grid).size();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            McDisplaysMod.LOGGER.warn("Failed to read AE2 network at {}", pos, exception);
            return -1;
        }
    }

    public static DisplayDocument extract(Level level, ItemStack stack) {
        Ae2MonitorData data = Ae2CpuMonitorItem.getData(stack);
        if (!data.linked()) {
            return DisplayDocument.message(TITLE,
                "This monitor is not linked to an ME network.",
                "",
                "Sneak-right-click any block of an ME network to link it.");
        }

        if (!isAe2Loaded()) {
            return DisplayDocument.message(TITLE, "Applied Energistics 2 is not installed.");
        }

        List<List<String>> live = null;
        Level target = resolveLevel(level, data.dimension());
        BlockPos pos = data.blockPos();
        if (target != null && target.hasChunkAt(pos)) {
            try {
                live = buildBlocks(target, pos);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                McDisplaysMod.LOGGER.warn("Failed to read AE2 crafting CPUs", exception);
            }
        }

        long now = System.currentTimeMillis();
        if (live != null) {
            List<String> pages = paginate(live);
            if (!pages.equals(data.cachePages()) && now - data.cacheTime() >= CACHE_REFRESH_MILLIS) {
                Ae2CpuMonitorItem.setData(stack, new Ae2MonitorData(data.dimension(), data.pos(), pages, now));
            }
            return new DisplayDocument(TITLE, pages);
        }

        if (data.cachePages().isEmpty()) {
            return DisplayDocument.message(TITLE, "[gold]ME network is not loaded.[/]", "No data has been recorded yet.");
        }

        List<String> pages = new ArrayList<>(data.cachePages());
        String warning = "[gold]Network not loaded - last update " + TIME_FORMAT.format(Instant.ofEpochMilli(data.cacheTime())) + "[/]\n";
        pages.set(0, warning + pages.get(0));
        return new DisplayDocument(TITLE, pages);
    }

    private static List<List<String>> buildBlocks(Level level, BlockPos pos) throws ReflectiveOperationException {
        Object grid = resolveGrid(level, pos);
        if (grid == null) {
            return List.of(List.of("[red]No ME network at the linked block.[/]"));
        }

        List<CpuInfo> infos = new ArrayList<>();
        int index = 0;
        for (Object cpu : cpus(grid)) {
            infos.add(describeCpu(cpu, ++index));
        }

        if (infos.isEmpty()) {
            return List.of(List.of("No crafting CPUs in this network."));
        }

        infos.sort(Comparator.comparing(CpuInfo::busy).reversed()
            .thenComparing(Comparator.comparingLong(CpuInfo::elapsedSeconds).reversed())
            .thenComparing(CpuInfo::name, String.CASE_INSENSITIVE_ORDER));

        long busy = infos.stream().filter(CpuInfo::busy).count();
        List<List<String>> blocks = new ArrayList<>();
        blocks.add(List.of("CPUs: " + infos.size() + "  busy: " + busy + "  idle: " + (infos.size() - busy), ""));
        for (CpuInfo info : infos) {
            blocks.add(info.lines());
        }
        return blocks;
    }

    private static CpuInfo describeCpu(Object cpu, int index) throws ReflectiveOperationException {
        Class<?> cpuType = Class.forName("appeng.api.networking.crafting.ICraftingCPU");
        boolean busy = Boolean.TRUE.equals(cpuType.getMethod("isBusy").invoke(cpu));
        long storage = ((Number) cpuType.getMethod("getAvailableStorage").invoke(cpu)).longValue();
        int coProcessors = ((Number) cpuType.getMethod("getCoProcessors").invoke(cpu)).intValue();
        Object nameComponent = cpuType.getMethod("getName").invoke(cpu);
        String name = nameComponent instanceof Component component ? DisplayContentExtractor.clean(component.getString()).trim() : "";
        if (name.isBlank()) {
            name = "CPU " + index;
        }

        String hardware = coProcessors + " cop, " + formatAmount(storage);
        if (!busy) {
            return new CpuInfo(name, false, 0L, List.of("[gray]" + name + ": idle (" + hardware + ")[/]"));
        }

        Object status = cpuType.getMethod("getJobStatus").invoke(cpu);
        if (status == null) {
            return new CpuInfo(name, true, 0L, List.of("[green]" + name + ": busy (" + hardware + ")[/]"));
        }

        Class<?> statusType = Class.forName("appeng.api.networking.crafting.CraftingJobStatus");
        long total = ((Number) statusType.getMethod("totalItems").invoke(status)).longValue();
        long progress = ((Number) statusType.getMethod("progress").invoke(status)).longValue();
        long elapsedSeconds = ((Number) statusType.getMethod("elapsedTimeNanos").invoke(status)).longValue() / 1_000_000_000L;
        String crafting = describeCrafting(statusType.getMethod("crafting").invoke(status));

        int percent = total > 0 ? (int) Math.min(100L, progress * 100L / total) : 0;
        String color = elapsedSeconds >= McDisplaysConfig.alertMinutes() * 60L ? "red"
            : elapsedSeconds >= McDisplaysConfig.warnMinutes() * 60L ? "gold" : "green";

        List<String> lines = List.of(
            "[" + color + "]" + name + ": " + crafting + "[/]",
            "[" + color + "]  " + percent + "% | " + formatDuration(elapsedSeconds) + " | " + hardware + "[/]"
        );
        return new CpuInfo(name, true, elapsedSeconds, lines);
    }

    private static String describeCrafting(Object genericStack) throws ReflectiveOperationException {
        if (genericStack == null) {
            return "?";
        }

        Class<?> stackType = Class.forName("appeng.api.stacks.GenericStack");
        Object key = stackType.getMethod("what").invoke(genericStack);
        long amount = ((Number) stackType.getMethod("amount").invoke(genericStack)).longValue();
        if (key == null) {
            return "?";
        }

        Class<?> keyType = Class.forName("appeng.api.stacks.AEKey");
        Object display = keyType.getMethod("getDisplayName").invoke(key);
        String name = display instanceof Component component ? DisplayContentExtractor.clean(component.getString()).trim() : "?";
        long perUnit = Math.max(1L, ((Number) keyType.getMethod("getAmountPerUnit").invoke(key)).longValue());
        return name + " x" + formatAmount(amount / perUnit);
    }

    static Object resolveGrid(Level level, BlockPos pos) throws ReflectiveOperationException {
        Class<?> helper = Class.forName("appeng.api.networking.GridHelper");
        Object host = helper.getMethod("getNodeHost", Level.class, BlockPos.class).invoke(null, level, pos);
        if (host == null) {
            return null;
        }

        Method getNode = Class.forName("appeng.api.networking.IInWorldGridNodeHost").getMethod("getGridNode", Direction.class);
        Object node = getNode.invoke(host, (Direction) null);
        for (Direction direction : Direction.values()) {
            if (node != null) {
                break;
            }
            node = getNode.invoke(host, direction);
        }

        if (node == null) {
            return null;
        }
        return Class.forName("appeng.api.networking.IGridNode").getMethod("getGrid").invoke(node);
    }

    private static Collection<?> cpus(Object grid) throws ReflectiveOperationException {
        Object service = Class.forName("appeng.api.networking.IGrid").getMethod("getCraftingService").invoke(grid);
        Object result = Class.forName("appeng.api.networking.crafting.ICraftingService").getMethod("getCpus").invoke(service);
        return result instanceof Collection<?> collection ? collection : List.of();
    }

    private static Level resolveLevel(Level current, String dimension) {
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        if (location == null) {
            return null;
        }
        if (current.dimension().location().equals(location)) {
            return current;
        }
        if (current.getServer() == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return current.getServer().getLevel(key);
    }

    private static List<String> paginate(List<List<String>> blocks) {
        List<String> lines = new ArrayList<>();
        for (List<String> block : blocks) {
            lines.addAll(block);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines.isEmpty() ? List.of("") : List.of(String.join("\n", lines));
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + String.format(Locale.ROOT, "%02d", secs) + "s";
        }
        return secs + "s";
    }

    private static String formatAmount(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    private record CpuInfo(String name, boolean busy, long elapsedSeconds, List<String> lines) {
    }
}
