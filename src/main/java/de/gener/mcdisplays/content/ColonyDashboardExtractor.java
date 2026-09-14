package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.ColonyDashboardItem;
import de.gener.mcdisplays.item.DashboardMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Extracts live MineColonies colony data for the Colony Dashboard item and
 * formats it into {@link DisplayDocument} pages for in-world rendering.
 * <p>
 * All MineColonies access is done through the reflection utilities in
 * {@link MinecoloniesReflectionCompat} so that this mod compiles and runs
 * without a hard dependency on MineColonies.
 */
public final class ColonyDashboardExtractor {
    private static final String TITLE = "Colony Dashboard";
    private static final int LINES_PER_PAGE = 10;

    private ColonyDashboardExtractor() {
    }

    public static DisplayDocument extract(Level level, ItemStack stack) {
        int colonyId = ColonyDashboardItem.getColonyId(stack);
        DashboardMode mode = ColonyDashboardItem.getMode(stack);
        String dimension = ColonyDashboardItem.getDimension(stack);

        if (colonyId < 0) {
            return DisplayDocument.message(TITLE,
                "This dashboard is not linked to a colony.",
                "",
                "Sneak-right-click a MineColonies building to link it.");
        }

        try {
            Object colony = MinecoloniesReflectionCompat.resolveColonyById(level, colonyId, dimension);
            if (colony == null) {
                return DisplayDocument.message(TITLE, "The linked colony is not loaded in this world.");
            }

            String colonyName = MinecoloniesReflectionCompat.cleanText(
                String.valueOf(MinecoloniesReflectionCompat.invoke(colony, "getName")));

            return switch (mode) {
                case CITIZENS -> extractCitizens(colony, colonyName);
                case BUILDERS -> extractBuilders(colony, colonyName);
                case SICK -> extractSick(colony, colonyName);
                case DEATHS -> extractDeaths(colony, colonyName);
                case STATISTICS -> extractStatistics(colony, colonyName);
                case WAREHOUSE -> extractWarehouse(colony, colonyName);
            };
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract Colony Dashboard data for mode {}", mode.id(), exception);
            return DisplayDocument.message(TITLE, "MineColonies data could not be resolved.");
        }
    }

    // ---- Citizens ----

    private static DisplayDocument extractCitizens(Object colony, String colonyName) {
        try {
            Collection<?> citizens = getCitizens(colony);
            Map<String, List<String>> byJob = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            int total = 0;

            for (Object citizen : citizens) {
                total++;
                String name = citizenName(citizen);
                String jobName = citizenJobName(citizen);
                byJob.computeIfAbsent(jobName, k -> new ArrayList<>()).add(name);
            }

            List<String> lines = new ArrayList<>();
            lines.add("Citizens: " + total);
            lines.add("");

            for (Map.Entry<String, List<String>> entry : byJob.entrySet()) {
                lines.add(entry.getKey() + " (" + entry.getValue().size() + "):");
                for (String name : entry.getValue()) {
                    lines.add("  " + name);
                }
                lines.add("");
            }

            if (total == 0) {
                lines.add("No citizens found.");
            }

            return new DisplayDocument("Citizens - " + colonyName,
                MinecoloniesReflectionCompat.toPages(lines, LINES_PER_PAGE));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract citizen data", exception);
            return DisplayDocument.message("Citizens - " + colonyName, "Could not retrieve citizen data.");
        }
    }

    // ---- Builders ----

    private static DisplayDocument extractBuilders(Object colony, String colonyName) {
        try {
            Object buildingManager = getBuildingManager(colony);
            Map<?, ?> buildings = asMap(MinecoloniesReflectionCompat.invoke(buildingManager, "getBuildings"));

            List<String> lines = new ArrayList<>();
            int builderCount = 0;

            // Find builder huts and show what each builder is doing.
            for (Map.Entry<?, ?> entry : buildings.entrySet()) {
                Object building = entry.getValue();

                // Check if this is a builder-type building (extends AbstractBuildingStructureBuilder).
                if (!isStructureBuilder(building)) {
                    continue;
                }

                builderCount++;
                String buildingName = resolveBuildingName(building);

                // Get the assigned citizen name(s).
                Collection<?> assignedCitizens = MinecoloniesReflectionCompat.asCollection(
                    MinecoloniesReflectionCompat.invokeOptional(building, "getAllAssignedCitizen"));
                String citizenNames = "";
                if (!assignedCitizens.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (Object citizen : assignedCitizens) {
                        names.add(citizenName(citizen));
                    }
                    citizenNames = String.join(", ", names);
                }

                if (!citizenNames.isBlank()) {
                    lines.add("- " + citizenNames);
                    lines.add("  (" + buildingName + ")");
                } else {
                    lines.add("- " + buildingName);
                    lines.add("  No worker assigned");
                }

                // Check if the builder has an active work order.
                boolean hasWork = Boolean.TRUE.equals(
                    MinecoloniesReflectionCompat.invokeOptional(building, "hasWorkOrder"));

                if (hasWork) {
                    Object workOrder = MinecoloniesReflectionCompat.invokeOptional(building, "getWorkOrder");
                    if (workOrder != null) {
                        String woName = safeString(MinecoloniesReflectionCompat.invokeOptional(workOrder, "getDisplayName"));
                        if (woName.isBlank()) {
                            String translationKey = safeString(MinecoloniesReflectionCompat.invokeOptional(workOrder, "getTranslationKey"));
                            if (!translationKey.isBlank()) {
                                woName = safeString(Component.translatable(translationKey));
                                if (woName.isBlank() || woName.equals(translationKey)) {
                                    woName = friendlyKey(translationKey);
                                }
                            }
                        }
                        if (woName.isBlank()) {
                            woName = "Work Order";
                        }

                        // Level upgrade info.
                        Object currentLevel = MinecoloniesReflectionCompat.invokeOptional(workOrder, "getCurrentLevel");
                        Object targetLevel = MinecoloniesReflectionCompat.invokeOptional(workOrder, "getTargetLevel");
                        if (currentLevel instanceof Number cur && targetLevel instanceof Number tgt && tgt.intValue() > 0) {
                            lines.add("  Building: " + woName + " Lv " + cur.intValue() + " -> " + tgt.intValue());
                        } else {
                            lines.add("  Building: " + woName);
                        }

                        // Progress stage (e.g. BUILDING, CLEARING, DECONSTRUCT).
                        Object stage = MinecoloniesReflectionCompat.invokeOptional(workOrder, "getStage");
                        if (stage != null) {
                            String stageName = friendlyKey(stage.toString());
                            if (!stageName.isBlank() && !stageName.equalsIgnoreCase("null")) {
                                lines.add("  Stage: " + stageName);
                            }
                        }
                    }
                } else {
                    lines.add("  Idle");
                }

                lines.add("");
            }

            // Also list unclaimed work orders waiting for a builder.
            Object workManager = MinecoloniesReflectionCompat.invoke(colony, "getWorkManager");
            Map<?, ?> workOrderMap = asMap(MinecoloniesReflectionCompat.invoke(workManager, "getWorkOrders"));

            List<String> unclaimedLines = new ArrayList<>();
            for (Object workOrder : workOrderMap.values()) {
                boolean claimed = Boolean.TRUE.equals(
                    MinecoloniesReflectionCompat.invokeOptional(workOrder, "isClaimed"));
                if (claimed) {
                    continue;
                }

                String woName = safeString(MinecoloniesReflectionCompat.invokeOptional(workOrder, "getDisplayName"));
                if (woName.isBlank()) {
                    woName = "Work Order";
                }

                Object currentLevel = MinecoloniesReflectionCompat.invokeOptional(workOrder, "getCurrentLevel");
                Object targetLevel = MinecoloniesReflectionCompat.invokeOptional(workOrder, "getTargetLevel");
                if (currentLevel instanceof Number cur && targetLevel instanceof Number tgt && tgt.intValue() > 0) {
                    unclaimedLines.add("- " + woName + " Lv " + cur.intValue() + " -> " + tgt.intValue());
                } else {
                    unclaimedLines.add("- " + woName);
                }
                unclaimedLines.add("  Waiting for builder");
                unclaimedLines.add("");
            }

            if (!unclaimedLines.isEmpty()) {
                lines.add("Unclaimed Work Orders:");
                lines.add("");
                lines.addAll(unclaimedLines);
            }

            if (builderCount == 0 && unclaimedLines.isEmpty()) {
                lines.add("No builders or work orders.");
            }

            lines.add(0, "Builders: " + builderCount);
            lines.add(1, "");

            return new DisplayDocument("Builders - " + colonyName,
                MinecoloniesReflectionCompat.toPages(lines, LINES_PER_PAGE));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract builder data", exception);
            return DisplayDocument.message("Builders - " + colonyName, "Could not retrieve builder data.");
        }
    }

    private static boolean isStructureBuilder(Object building) {
        // Walk the class hierarchy to check for AbstractBuildingStructureBuilder.
        Class<?> clazz = building.getClass();
        while (clazz != null) {
            if (clazz.getSimpleName().equals("AbstractBuildingStructureBuilder")) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    // ---- Sick ----

    private static DisplayDocument extractSick(Object colony, String colonyName) {
        try {
            Collection<?> citizens = getCitizens(colony);
            List<String> lines = new ArrayList<>();
            int sickCount = 0;

            for (Object citizen : citizens) {
                if (!isCitizenSick(citizen)) {
                    continue;
                }

                sickCount++;
                String name = citizenName(citizen);
                String jobName = citizenJobName(citizen);

                lines.add("- " + name);
                lines.add("  Job: " + jobName);

                Object homeBuilding = MinecoloniesReflectionCompat.invokeOptional(citizen, "getHomeBuilding");
                if (homeBuilding instanceof BlockPos pos) {
                    lines.add("  Home: " + formatBlockPos(pos));
                }

                String disease = getDiseaseString(citizen);
                if (!disease.isBlank()) {
                    lines.add("  Condition: " + disease);
                }

                lines.add("");
            }

            lines.add(0, "Sick Citizens: " + sickCount);
            lines.add(1, "");

            if (sickCount == 0) {
                lines.add("All citizens are healthy.");
            }

            return new DisplayDocument("Sick - " + colonyName,
                MinecoloniesReflectionCompat.toPages(lines, LINES_PER_PAGE));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract sick citizen data", exception);
            return DisplayDocument.message("Sick - " + colonyName, "Could not retrieve health data.");
        }
    }

    // ---- Deaths ----

    private static DisplayDocument extractDeaths(Object colony, String colonyName) {
        try {
            List<String> lines = new ArrayList<>();

            // Try to get the death list from the grave manager.
            List<String> deathLines = extractDeathsFromGraveManager(colony);
            if (!deathLines.isEmpty()) {
                lines.addAll(deathLines);
            }

            // If the grave manager didn't yield anything, try the event log.
            if (lines.isEmpty()) {
                deathLines = extractDeathsFromEventManager(colony);
                lines.addAll(deathLines);
            }

            // Try to get the lifetime death count from statistics.
            String deathStat = getStatistic(colony, "deaths");
            if (!deathStat.isBlank()) {
                if (!lines.isEmpty()) {
                    lines.add("");
                }
                lines.add("Total Deaths: " + deathStat);
            }

            if (lines.isEmpty()) {
                lines.add("No death records found.");
            }

            lines.add(0, "Death Log");
            lines.add(1, "");

            return new DisplayDocument("Deaths - " + colonyName,
                MinecoloniesReflectionCompat.toPages(lines, LINES_PER_PAGE));
        } catch (RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract death data", exception);
            return DisplayDocument.message("Deaths - " + colonyName, "Could not retrieve death records.");
        }
    }

    // ---- Statistics ----

    private static DisplayDocument extractStatistics(Object colony, String colonyName) {
        try {
            List<String> lines = new ArrayList<>();

            // Known MineColonies statistic keys from StatisticsConstants.
            String[][] statDefs = {
                {"death", "Deaths"},
                {"birth", "Births"},
                {"mobs_killed", "Mobs Killed"},
                {"ores_mined", "Ores Mined"},
                {"blocks_mined", "Blocks Mined"},
                {"blocks_placed", "Blocks Placed"},
                {"trees_cut", "Trees Cut"},
                {"crops_harvested", "Crops Harvested"},
                {"fish_caught", "Fish Caught"},
                {"items_crafted", "Items Crafted"},
                {"items_delivered", "Items Delivered"},
                {"food_served", "Food Served"},
                {"citizens_healed", "Citizens Healed"},
                {"land_tilled", "Land Tilled"},
                {"build_built", "Buildings Built"},
                {"build_upgraded", "Buildings Upgraded"},
                {"build_repaired", "Buildings Repaired"},
                {"items_cooked", "Items Cooked"},
                {"animals_butchered", "Animals Butchered"},
                {"diseases_treated", "Diseases Treated"},
                {"flowers_picked", "Flowers Picked"},
                {"items_brewed", "Items Brewed"},
                {"deliveries_made", "Deliveries Made"},
                {"graves_dug", "Graves Dug"},
                {"citizens_resurrected", "Citizens Resurrected"},
                {"research_completed", "Research Completed"},
            };

            Object statsManager = MinecoloniesReflectionCompat.invokeOptional(colony, "getStatisticsManager");
            if (statsManager != null) {
                // Primary: use known stat keys with getStatTotal(String)
                for (String[] pair : statDefs) {
                    String value = getStatisticFromManager(statsManager, pair[0]);
                    if (!value.isBlank() && !value.equals("0")) {
                        lines.add("  " + pair[1] + ": " + value);
                    }
                }

                // If no known keys matched, discover all stat types
                if (lines.isEmpty()) {
                    Collection<?> statTypes = MinecoloniesReflectionCompat.asCollection(
                        MinecoloniesReflectionCompat.invokeOptional(statsManager, "getStatTypes"));
                    for (Object statType : statTypes) {
                        String key = String.valueOf(statType);
                        String value = getStatisticFromManager(statsManager, key);
                        if (!value.isBlank() && !value.equals("0")) {
                            lines.add("  " + friendlyKey(key) + ": " + value);
                        }
                    }
                }
            }

            if (lines.isEmpty()) {
                lines.add("No statistics recorded yet.");
            } else {
                lines.add(0, "Colony Statistics");
                lines.add(1, "");
            }

            return new DisplayDocument("Statistics - " + colonyName,
                MinecoloniesReflectionCompat.toPages(lines, LINES_PER_PAGE));
        } catch (RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract statistics data", exception);
            return DisplayDocument.message("Statistics - " + colonyName, "Could not retrieve statistics.");
        }
    }

    // ---- Warehouse ----

    private static DisplayDocument extractWarehouse(Object colony, String colonyName) {
        try {
            Object buildingManager = getBuildingManager(colony);
            Map<?, ?> buildings = asMap(MinecoloniesReflectionCompat.invoke(buildingManager, "getBuildings"));

            List<String> lines = new ArrayList<>();
            int warehouseCount = 0;

            for (Map.Entry<?, ?> entry : buildings.entrySet()) {
                Object building = entry.getValue();
                if (!isWarehouse(building)) {
                    continue;
                }

                warehouseCount++;
                BlockPos pos = entry.getKey() instanceof BlockPos bp ? bp : null;
                String posLabel = pos != null ? " (" + formatBlockPos(pos) + ")" : "";

                String name = safeString(MinecoloniesReflectionCompat.invokeOptional(building, "getBuildingDisplayName"));
                if (name.isBlank()) {
                    name = "Warehouse";
                }

                lines.add("- " + name + posLabel);

                Object level = MinecoloniesReflectionCompat.invokeOptional(building, "getBuildingLevel");
                if (level instanceof Number num && num.intValue() > 0) {
                    lines.add("  Level: " + num.intValue());
                }

                Boolean isFull = asBool(MinecoloniesReflectionCompat.invokeOptional(building, "isFull"));
                if (isFull != null) {
                    lines.add("  Status: " + (isFull ? "FULL" : "OK"));
                }

                lines.add("");
            }

            if (warehouseCount == 0) {
                lines.add("No warehouses found.");
            }

            lines.add(0, "Warehouses: " + warehouseCount);
            lines.add(1, "");

            return new DisplayDocument("Warehouse - " + colonyName,
                MinecoloniesReflectionCompat.toPages(lines, LINES_PER_PAGE));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract warehouse data", exception);
            return DisplayDocument.message("Warehouse - " + colonyName, "Could not retrieve warehouse data.");
        }
    }

    // ---- Shared citizen helpers ----

    private static Collection<?> getCitizens(Object colony) throws ReflectiveOperationException {
        Object citizenManager = MinecoloniesReflectionCompat.invokeOptional(colony, "getCitizenManager");
        if (citizenManager != null) {
            Object citizens = MinecoloniesReflectionCompat.invokeOptional(citizenManager, "getCitizens");
            if (citizens != null) {
                return MinecoloniesReflectionCompat.asCollection(citizens);
            }
        }

        // Fallback: some versions expose getCitizens directly on the colony.
        return MinecoloniesReflectionCompat.asCollection(
            MinecoloniesReflectionCompat.invoke(colony, "getCitizens"));
    }

    private static String citizenName(Object citizen) {
        Object name = MinecoloniesReflectionCompat.invokeOptional(citizen, "getName");
        String text = MinecoloniesReflectionCompat.componentToString(name);
        return text.isBlank() ? "Unknown" : text;
    }

    private static String citizenJobName(Object citizen) {
        Object job = MinecoloniesReflectionCompat.invokeOptional(citizen, "getJob");
        if (job == null) {
            job = MinecoloniesReflectionCompat.invokeOptional(citizen, "getJobView");
        }
        if (job == null) {
            return "Unemployed";
        }

        // Try the job's display name.
        Object nameComponent = MinecoloniesReflectionCompat.invokeOptional(job, "getName");
        if (nameComponent != null) {
            String text = MinecoloniesReflectionCompat.componentToString(nameComponent);
            if (!text.isBlank()) {
                return text;
            }
        }

        // Try the registry entry translation key.
        Object registryEntry = MinecoloniesReflectionCompat.invokeOptional(job, "getJobRegistryEntry");
        if (registryEntry != null) {
            Object translationKey = MinecoloniesReflectionCompat.invokeOptional(registryEntry, "getTranslationKey");
            if (translationKey != null) {
                String key = String.valueOf(translationKey);
                String translated = MinecoloniesReflectionCompat.componentToString(Component.translatable(key));
                if (!translated.equals(key) && !translated.isBlank()) {
                    return translated;
                }
            }
        }

        // Last resort: derive from class name.
        String className = job.getClass().getSimpleName();
        return friendlyKey(className.replace("Job", "").replace("View", ""));
    }

    private static boolean isCitizenSick(Object citizen) {
        // Primary: disease handler
        Object diseaseHandler = MinecoloniesReflectionCompat.invokeOptional(citizen, "getCitizenDiseaseHandler");
        if (diseaseHandler != null) {
            Object sick = MinecoloniesReflectionCompat.invokeOptional(diseaseHandler, "isSick");
            if (Boolean.TRUE.equals(sick)) {
                return true;
            }
        }

        // Secondary: entity health check
        Object entityOptional = MinecoloniesReflectionCompat.invokeOptional(citizen, "getEntity");
        if (entityOptional != null) {
            Object entity = MinecoloniesReflectionCompat.invokeOptional(entityOptional, "orElse", (Object) null);
            if (entity != null) {
                Object health = MinecoloniesReflectionCompat.invokeOptional(entity, "getHealth");
                Object maxHealth = MinecoloniesReflectionCompat.invokeOptional(entity, "getMaxHealth");
                if (health instanceof Number h && maxHealth instanceof Number mh) {
                    if (mh.floatValue() > 0 && h.floatValue() < mh.floatValue() * 0.3f) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static String getDiseaseString(Object citizen) {
        Object diseaseHandler = MinecoloniesReflectionCompat.invokeOptional(citizen, "getCitizenDiseaseHandler");
        if (diseaseHandler == null) {
            return "";
        }

        Object disease = MinecoloniesReflectionCompat.invokeOptional(diseaseHandler, "getDisease");
        if (disease == null) {
            return "";
        }

        String text = MinecoloniesReflectionCompat.cleanText(String.valueOf(disease));
        return text.equals("null") ? "" : text;
    }

    // ---- Death helpers ----

    private static List<String> extractDeathsFromGraveManager(Object colony) {
        List<String> lines = new ArrayList<>();
        Object graveManager = MinecoloniesReflectionCompat.invokeOptional(colony, "getGraveManager");
        if (graveManager == null) {
            return lines;
        }

        Map<?, ?> graves = asMap(MinecoloniesReflectionCompat.invokeOptional(graveManager, "getGraves"));
        for (Map.Entry<?, ?> entry : graves.entrySet()) {
            BlockPos pos = entry.getKey() instanceof BlockPos bp ? bp : null;
            Object graveData = entry.getValue();
            String citizenName = safeString(MinecoloniesReflectionCompat.invokeOptional(graveData, "getCitizenName"));
            if (citizenName.isBlank()) {
                citizenName = "Unknown";
            }

            lines.add("- " + citizenName);
            if (pos != null) {
                lines.add("  Grave: " + formatBlockPos(pos));
            }
            lines.add("");
        }
        return lines;
    }

    private static List<String> extractDeathsFromEventManager(Object colony) {
        List<String> lines = new ArrayList<>();
        Object eventManager = MinecoloniesReflectionCompat.invokeOptional(colony, "getEventDescriptionManager");
        if (eventManager == null) {
            eventManager = MinecoloniesReflectionCompat.invokeOptional(colony, "getEventManager");
        }
        if (eventManager == null) {
            return lines;
        }

        Collection<?> events = MinecoloniesReflectionCompat.asCollection(
            MinecoloniesReflectionCompat.invokeOptional(eventManager, "getEventDescriptions"));
        for (Object event : events) {
            String eventName = safeString(MinecoloniesReflectionCompat.invokeOptional(event, "getName"));
            String eventType = event.getClass().getSimpleName();
            if (eventType.toLowerCase().contains("death") || eventType.toLowerCase().contains("citizen")) {
                if (!eventName.isBlank()) {
                    lines.add("- " + eventName);
                    lines.add("");
                }
            }
        }
        return lines;
    }

    // ---- Statistics helpers ----

    private static String getStatistic(Object colony, String key) {
        Object statsManager = MinecoloniesReflectionCompat.invokeOptional(colony, "getStatisticsManager");
        if (statsManager != null) {
            return getStatisticFromManager(statsManager, key);
        }
        return "";
    }

    private static String getStatisticFromManager(Object statsManager, String key) {
        // IStatisticsManager.getStatTotal(String) returns int
        Object value = MinecoloniesReflectionCompat.invokeOptional(statsManager, "getStatTotal", key);
        if (value instanceof Number num && num.longValue() > 0) {
            return String.valueOf(num.longValue());
        }

        return "";
    }

    // ---- Building helpers ----

    private static String resolveBuildingName(Object building) {
        // 1. Try custom name (player-renamed building).
        String customName = safeString(MinecoloniesReflectionCompat.invokeOptional(building, "getCustomName"));
        if (!customName.isBlank()) {
            return customName;
        }

        // 2. Try getBuildingDisplayName() — this often returns a translation key
        //    like "com.minecolonies.building.builder".
        String displayName = safeString(MinecoloniesReflectionCompat.invokeOptional(building, "getBuildingDisplayName"));
        if (!displayName.isBlank()) {
            // If it looks like a translation key (contains dots, no spaces), translate it.
            if (displayName.contains(".") && !displayName.contains(" ")) {
                String translated = safeString(Component.translatable(displayName));
                if (!translated.isBlank() && !translated.equals(displayName)) {
                    return translated;
                }
                // Extract the last segment and make it friendly: "com.minecolonies.building.builder" -> "Builder"
                String[] parts = displayName.split("\\.");
                return friendlyKey(parts[parts.length - 1]);
            }
            return displayName;
        }

        // 3. Try getSchematicName() as a last resort.
        String schematicName = safeString(MinecoloniesReflectionCompat.invokeOptional(building, "getSchematicName"));
        if (!schematicName.isBlank()) {
            return friendlyKey(schematicName);
        }

        return "Builder's Hut";
    }

    private static Object getBuildingManager(Object colony) throws ReflectiveOperationException {
        Object manager = MinecoloniesReflectionCompat.invokeOptional(colony, "getServerBuildingManager");
        if (manager == null) {
            manager = MinecoloniesReflectionCompat.invokeOptional(colony, "getClientBuildingManager");
        }
        if (manager == null) {
            manager = MinecoloniesReflectionCompat.invoke(colony, "getBuildingManager");
        }
        return manager;
    }

    private static boolean isWarehouse(Object building) {
        // Check class hierarchy for warehouse type names.
        String className = building.getClass().getName().toLowerCase();
        if (className.contains("warehouse")) {
            return true;
        }

        // Check the building's schematic name.
        String schematicName = safeString(
            MinecoloniesReflectionCompat.invokeOptional(building, "getSchematicName"));
        return schematicName.toLowerCase().contains("warehouse");
    }

    // ---- Formatting helpers ----

    private static String safeString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Component comp) {
            return MinecoloniesReflectionCompat.cleanText(comp.getString());
        }
        return MinecoloniesReflectionCompat.cleanText(String.valueOf(value));
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private static Boolean asBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return null;
    }

    private static String friendlyKey(String key) {
        if (key == null || key.isBlank()) {
            return "Unknown";
        }

        // Convert camelCase and snake_case to title case.
        StringBuilder result = new StringBuilder(key.length() + 4);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-') {
                result.append(' ');
            } else if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(key.charAt(i - 1))) {
                result.append(' ').append(c);
            } else if (result.isEmpty() || result.charAt(result.length() - 1) == ' ') {
                result.append(Character.toUpperCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString().trim();
    }
}
