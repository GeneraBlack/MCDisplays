package de.gener.mcdisplays;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class McDisplaysConfig {
    private static final String TRANSLATION_PREFIX = McDisplaysMod.MODID + ".configuration";

    public static final McDisplaysConfig INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<McDisplaysConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(McDisplaysConfig::new);
        INSTANCE = specPair.getLeft();
        SPEC = specPair.getRight();
    }

    private final ForgeConfigSpec.IntValue refreshIntervalTicks;
    private final ForgeConfigSpec.IntValue maxDisplayWidth;
    private final ForgeConfigSpec.IntValue maxDisplayHeight;
    private final ForgeConfigSpec.BooleanValue allowManualPageTurning;

    private McDisplaysConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Gameplay settings for MC Displays.")
            .translation(TRANSLATION_PREFIX + ".section.display")
            .push("display");

        refreshIntervalTicks = builder
            .translation(TRANSLATION_PREFIX + ".display.refreshIntervalTicks")
            .comment("How often the source item is re-read on the server, in ticks.", "20 ticks = 1 second.")
            .defineInRange("refreshIntervalTicks", 100, 20, 1200);
        maxDisplayWidth = builder
            .translation(TRANSLATION_PREFIX + ".display.maxDisplayWidth")
            .comment("Maximum width of a rectangular multiblock display in panels.")
            .defineInRange("maxDisplayWidth", 8, 1, 16);
        maxDisplayHeight = builder
            .translation(TRANSLATION_PREFIX + ".display.maxDisplayHeight")
            .comment("Maximum height of a rectangular multiblock display in panels.")
            .defineInRange("maxDisplayHeight", 6, 1, 16);
        allowManualPageTurning = builder
            .translation(TRANSLATION_PREFIX + ".display.allowManualPageTurning")
            .comment("Allows page turning with an empty main hand.", "Right half advances, left half goes back. Sneak-right-click still removes the source item.")
            .define("allowManualPageTurning", true);

        builder.pop();
    }

    public static int refreshIntervalTicks() {
        return INSTANCE.refreshIntervalTicks.get();
    }

    public static int maxDisplayWidth() {
        return INSTANCE.maxDisplayWidth.get();
    }

    public static int maxDisplayHeight() {
        return INSTANCE.maxDisplayHeight.get();
    }

    public static boolean allowManualPageTurning() {
        return INSTANCE.allowManualPageTurning.get();
    }
}