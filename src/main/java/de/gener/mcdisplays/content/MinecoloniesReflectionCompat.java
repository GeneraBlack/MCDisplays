package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysMod;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class MinecoloniesReflectionCompat {
    private static final ResourceLocation RESOURCE_SCROLL_ID = ResourceLocation.parse("minecolonies:resourcescroll");
    private static final ResourceLocation CLIPBOARD_ID = ResourceLocation.parse("minecolonies:clipboard");
    private static final String RESOURCE_SCROLL_TITLE = "Resource Scroll";
    private static final String CLIPBOARD_TITLE = "Clipboard";
    private static final String TAG_COLONY = "colony";
    private static final String TAG_BUILDER = "builder";
    private static final String TAG_WAREHOUSE_SNAPSHOT = "version";
    private static final String TAG_HIDE_UNIMPORTANT = "hideunimportant";
    private static final String TAG_COMPONENTS = "components";
    private static final String TAG_ID = "id";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_SNAPSHOT = "snapshot";
    private static final String COMPONENT_COLONY_ID = "minecolonies:colony_id";
    private static final String COMPONENT_BUILDING_ID = "minecolonies:building_id";
    private static final String COMPONENT_WAREHOUSE_SNAPSHOT = "minecolonies:warehouse_snapshot";
    private static final String GET_REQUEST_FOR_TOKEN = "getRequestForToken";
    private static final Set<String> RESOURCE_SCROLL_DATA_KEYS = Set.of(TAG_COLONY, TAG_BUILDER, TAG_WAREHOUSE_SNAPSHOT);
    private static final Set<String> CLIPBOARD_DATA_KEYS = Set.of(TAG_COLONY, TAG_HIDE_UNIMPORTANT);
    private static final Map<String, Integer> RESOURCE_STATUS_PRIORITY = Map.of(
        "DONT_HAVE", 5,
        "NEED_MORE", 4,
        "IN_DELIVERY", 3,
        "HAVE_ENOUGH", 2,
        "NOT_NEEDED", 1
    );
    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(
        boolean.class, Boolean.class,
        byte.class, Byte.class,
        short.class, Short.class,
        int.class, Integer.class,
        long.class, Long.class,
        float.class, Float.class,
        double.class, Double.class,
        char.class, Character.class
    );

    private MinecoloniesReflectionCompat() {
    }

    public static boolean isSupported(ResourceLocation itemId) {
        return isResourceScroll(itemId) || isClipboard(itemId);
    }

    public static boolean isResourceScroll(ResourceLocation itemId) {
        return Objects.equals(itemId, RESOURCE_SCROLL_ID);
    }

    public static boolean isClipboard(ResourceLocation itemId) {
        return Objects.equals(itemId, CLIPBOARD_ID);
    }

    public static DisplayDocument extractResourceScroll(Level level, ItemStack stack) {
        CompoundTag data = getItemData(level, stack, RESOURCE_SCROLL_DATA_KEYS);
        CompoundTag snapshot = extractWarehouseSnapshot(data);
        int colonyId = data.contains(TAG_COLONY, Tag.TAG_INT) ? data.getInt(TAG_COLONY) : -1;
        BlockPos builderPos = readBlockPos(data, TAG_BUILDER);

        if (colonyId < 0 || builderPos == null) {
            if (!snapshot.isEmpty()) {
                return new DisplayDocument(RESOURCE_SCROLL_TITLE, toPages(describeSnapshot(snapshot), 10));
            }
            return DisplayDocument.message(RESOURCE_SCROLL_TITLE, "Link this scroll to a builder and snapshot a warehouse first.");
        }

        try {
            Object colony = resolveColony(level, data, colonyId);
            if (colony == null) {
                return fallbackResourceScroll(snapshot, builderPos, "The linked colony is not loaded in this world.");
            }

            Object builder = resolveBuilder(colony, builderPos);
            String colonyName = cleanText(String.valueOf(invoke(colony, "getName")));
            List<String> lines = new ArrayList<>();
            lines.add("Col #" + colonyId + ": " + normalizeTitle(colonyName, "Colony"));

            List<String> resourceLines = describeBuilderResources(builder, snapshot);
            if (!resourceLines.isEmpty()) {
                lines.add("");
                lines.addAll(resourceLines);
            } else if (!snapshot.isEmpty()) {
                lines.add("");
                lines.addAll(describeSnapshot(snapshot));
            } else {
                lines.add("");
                lines.add("No active builder resources found.");
            }

            return new DisplayDocument("Builder " + formatCompactBlockPos(builderPos), toPages(lines, 10));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract resource scroll data from MineColonies", exception);
            return fallbackResourceScroll(snapshot, builderPos, "MineColonies builder resources could not be resolved.");
        }
    }

    public static DisplayDocument extractClipboard(Level level, ItemStack stack) {
        CompoundTag data = getItemData(level, stack, CLIPBOARD_DATA_KEYS);
        if (!data.contains(TAG_COLONY, Tag.TAG_INT)) {
            return DisplayDocument.message(CLIPBOARD_TITLE, "This clipboard is not linked to a colony.");
        }

        int colonyId = data.getInt(TAG_COLONY);
        boolean showImportant = data.getBoolean(TAG_HIDE_UNIMPORTANT);

        try {
            Object colony = resolveColony(level, data, colonyId);
            if (colony == null) {
                return DisplayDocument.message(CLIPBOARD_TITLE, "The linked colony is not loaded in this world.");
            }

            String colonyName = cleanText(String.valueOf(invoke(colony, "getName")));
            Object requestManager = invoke(colony, "getRequestManager");
            LinkedHashSet<Object> rootRequests = new LinkedHashSet<>();
            Set<Object> asyncRequests = showImportant ? Set.of() : collectAsyncRequests(colony);

            collectRootRequests(rootRequests, requestManager, invoke(requestManager, "getPlayerResolver"));
            collectRootRequests(rootRequests, requestManager, invoke(requestManager, "getRetryingRequestResolver"));

            List<String> lines = new ArrayList<>();
            lines.add(showImportant ? "Showing: all requests" : "Showing: important requests");
            lines.add("");

            List<Object> sortedRequests = rootRequests.stream()
                .filter(request -> showImportant || !isMinimumStack(request))
                .filter(request -> showImportant || !asyncRequests.contains(requestId(request)))
                .sorted(requestComparator(requestManager))
                .toList();

            LinkedHashSet<Object> visited = new LinkedHashSet<>();
            for (Object request : sortedRequests) {
                appendRequestTreeLines(lines, requestManager, request, 0, visited);
                lines.add("");
            }

            while (!lines.isEmpty() && lines.getLast().isBlank()) {
                lines.removeLast();
            }

            if (lines.size() <= 1) {
                lines.clear();
                lines.add("No open requests.");
            }

            if (lines.isEmpty()) {
                lines.add("No open requests.");
            }

            return new DisplayDocument("Clipboard - " + normalizeTitle(colonyName, "Colony"), toPages(lines, 10));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to extract clipboard data from MineColonies", exception);
            return DisplayDocument.message(CLIPBOARD_TITLE, "MineColonies requests could not be resolved.");
        }
    }

    private static void collectRootRequests(LinkedHashSet<Object> rootRequests, Object requestManager, Object resolver) throws ReflectiveOperationException {
        if (requestManager == null || resolver == null) {
            return;
        }

        Collection<?> tokens = asCollection(invoke(resolver, "getAllAssignedRequests"));
        for (Object token : tokens) {
            Object request = invoke(requestManager, GET_REQUEST_FOR_TOKEN, token);
            while (request != null && Boolean.TRUE.equals(invoke(request, "hasParent"))) {
                request = invoke(requestManager, GET_REQUEST_FOR_TOKEN, invoke(request, "getParent"));
            }
            if (request != null) {
                rootRequests.add(request);
            }
        }
    }

    private static void appendRequestTreeLines(List<String> lines, Object requestManager, Object request, int depth, Set<Object> visited) {
        Object requestId = requestId(request);
        if (requestId != null && !visited.add(requestId)) {
            return;
        }

        String indent = "  ".repeat(Math.max(0, depth));
        String shortText = describeRequest(request);
        String requester = describeRequester(requestManager, request);
        if (shortText.isBlank()) {
            shortText = "Request";
        }

        lines.add(indent + formatRequestLine(requester, shortText));

        String longText = describeLongRequest(request);
        if (!longText.isBlank() && !longText.equals(shortText)) {
            lines.add(indent + "  " + longText);
        }

        Collection<?> children = asCollection(invokeOptional(request, "getChildren"));
        for (Object childToken : children) {
            Object child = invokeOptional(requestManager, GET_REQUEST_FOR_TOKEN, childToken);
            if (child != null) {
                appendRequestTreeLines(lines, requestManager, child, depth + 1, visited);
            }
        }
    }

    private static boolean isMinimumStack(Object request) {
        try {
            Object type = invoke(request, "getType");
            return String.valueOf(type).contains("MinimumStack");
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static String describeRequester(Object requestManager, Object request) {
        try {
            Object requester = invoke(request, "getRequester");
            if (requester == null) {
                return "";
            }

            Object display = invoke(requester, "getRequesterDisplayName", requestManager, request);
            return componentToString(display);
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private static String describeRequest(Object request) {
        try {
            Object display = invoke(request, "getShortDisplayString");
            return componentToString(display);
        } catch (ReflectiveOperationException exception) {
            return "Request";
        }
    }

    private static String describeLongRequest(Object request) {
        try {
            Object display = invoke(request, "getLongDisplayString");
            return componentToString(display);
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private static Object resolveColony(Level level, CompoundTag data, int colonyId) throws ReflectiveOperationException {
        Level linkedLevel = resolveLinkedLevel(level, data);
        if (linkedLevel == null) {
            return null;
        }

        Class<?> colonyManagerClass = Class.forName("com.minecolonies.api.colony.IColonyManager");
        Object colonyManager = colonyManagerClass.getMethod("getInstance").invoke(null);

        try {
            return invoke(colonyManager, "getColonyByWorld", colonyId, linkedLevel);
        } catch (NoSuchMethodException ignored) {
            return invoke(colonyManager, "getColonyByDimension", colonyId, linkedLevel.dimension());
        }
    }

    private static Level resolveLinkedLevel(Level currentLevel, CompoundTag data) {
        if (!data.contains(TAG_DIMENSION, Tag.TAG_STRING)) {
            return currentLevel;
        }

        String dimensionId = data.getString(TAG_DIMENSION);
        if (dimensionId.isBlank()) {
            return currentLevel;
        }

        ResourceLocation dimensionLocation;
        try {
            dimensionLocation = ResourceLocation.parse(dimensionId);
        } catch (RuntimeException ignored) {
            return currentLevel;
        }

        if (Objects.equals(currentLevel.dimension().location(), dimensionLocation)) {
            return currentLevel;
        }

        if (currentLevel.getServer() == null) {
            return null;
        }

        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        return currentLevel.getServer().getLevel(levelKey);
    }

    private static Object resolveBuilder(Object colony, BlockPos builderPos) {
        Object buildingManager = invokeOptional(colony, "getServerBuildingManager");
        if (buildingManager == null) {
            buildingManager = invokeOptional(colony, "getClientBuildingManager");
        }

        if (buildingManager == null) {
            return null;
        }

        return invokeOptional(buildingManager, "getBuilding", builderPos);
    }

    private static List<String> describeBuilderResources(Object builder, CompoundTag snapshot) throws ReflectiveOperationException {
        Map<?, ?> resources = asMap(invokeOptional(builder, "getNeededResources"));
        if (resources.isEmpty()) {
            return List.of();
        }

        boolean includeWarehouse = !snapshot.isEmpty();
        List<ResourceLine> entries = new ArrayList<>();
        for (Object value : resources.values()) {
            if (value != null) {
                entries.add(describeResource(value, snapshot, includeWarehouse));
            }
        }

        entries.removeIf(entry -> entry.required() <= 0 || entry.name().isBlank());
        entries.sort(Comparator.comparingInt(ResourceLine::priority).reversed().thenComparing(ResourceLine::name, String.CASE_INSENSITIVE_ORDER));

        List<String> lines = new ArrayList<>(entries.size());
        for (ResourceLine entry : entries) {
            lines.add(entry.format(includeWarehouse));
        }
        return lines;
    }

    private static ResourceLine describeResource(Object resource, CompoundTag snapshot, boolean includeWarehouse) throws ReflectiveOperationException {
        ItemStack resourceStack = (ItemStack) invoke(resource, "getItemStack");
        String name = cleanText(resourceStack.getHoverName().getString());
        int required = asInt(invoke(resource, "getAmount"));
        int onsite = asInt(invoke(resource, "getAvailable"));
        int delivery = asInt(invokeOptional(resource, "getAmountInDelivery"));
        String status = String.valueOf(invokeOptional(resource, "getAvailabilityStatus"));
        long warehouse = includeWarehouse ? readWarehouseCount(snapshot, resourceStack) : 0L;
        int priority = RESOURCE_STATUS_PRIORITY.getOrDefault(status, 0);
        return new ResourceLine(name, required, onsite, warehouse, delivery, status, priority);
    }

    private static long readWarehouseCount(CompoundTag snapshot, ItemStack resourceStack) {
        String snapshotKey = buildSnapshotKey(resourceStack);
        if (!snapshot.contains(snapshotKey)) {
            return 0L;
        }
        return extractNumber(snapshot.get(snapshotKey));
    }

    private static String buildSnapshotKey(ItemStack resourceStack) {
        int hashCode = 0;

        try {
            if (Boolean.TRUE.equals(invoke(resourceStack, "hasTag"))) {
                Object tag = invoke(resourceStack, "getTag");
                if (tag != null) {
                    hashCode = tag.hashCode();
                }
            }
        } catch (ReflectiveOperationException ignored) {
            CustomData customData = resourceStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                hashCode = customData.copyTag().hashCode();
            }
        }

        return resourceStack.getDescriptionId() + "-" + hashCode;
    }

    private static Set<Object> collectAsyncRequests(Object colony) {
        LinkedHashSet<Object> asyncRequests = new LinkedHashSet<>();
        for (Object citizen : asCollection(invokeOptional(colony, "getCitizens"))) {
            Object job = invokeOptional(citizen, "getJob");
            if (job == null) {
                job = invokeOptional(citizen, "getJobView");
            }
            if (job != null) {
                asyncRequests.addAll(asCollection(invokeOptional(job, "getAsyncRequests")));
            }
        }
        return asyncRequests;
    }

    private static Comparator<Object> requestComparator(Object requestManager) {
        return Comparator.comparing((Object request) -> describeRequester(requestManager, request), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(MinecoloniesReflectionCompat::describeRequest, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(MinecoloniesReflectionCompat::requestIdString, String.CASE_INSENSITIVE_ORDER);
    }

    private static Object requestId(Object request) {
        return invokeOptional(request, "getId");
    }

    private static String requestIdString(Object request) {
        Object requestId = requestId(request);
        return requestId == null ? "" : cleanText(String.valueOf(requestId));
    }

    private static DisplayDocument fallbackResourceScroll(CompoundTag snapshot, BlockPos builderPos, String message) {
        if (snapshot.isEmpty()) {
            return DisplayDocument.message(RESOURCE_SCROLL_TITLE, message);
        }

        List<String> lines = new ArrayList<>();
        if (builderPos != null) {
            lines.add("Builder: " + formatBlockPos(builderPos));
            lines.add("");
        }
        lines.addAll(describeSnapshot(snapshot));
        return new DisplayDocument(builderPos != null ? "Builder " + formatCompactBlockPos(builderPos) : RESOURCE_SCROLL_TITLE, toPages(lines, 10));
    }

    private static CompoundTag getItemData(Level level, ItemStack stack, Set<String> expectedKeys) {
        CompoundTag merged = new CompoundTag();

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        mergeRelevantData(merged, customData.copyTag(), expectedKeys);

        Object legacyTag = invokeOptional(stack, "getTag");
        if (legacyTag instanceof CompoundTag compoundTag) {
            mergeRelevantData(merged, compoundTag, expectedKeys);
        }

        Tag serializedStack = stack.save(level.registryAccess());
        if (serializedStack instanceof CompoundTag serializedCompound) {
            mergeMinecoloniesComponents(merged, serializedCompound, expectedKeys);
            mergeRelevantData(merged, findRelevantCompound(serializedCompound, expectedKeys), expectedKeys);
        }
        return merged;
    }

    private static void mergeMinecoloniesComponents(CompoundTag target, CompoundTag serializedStack, Set<String> expectedKeys) {
        if (!serializedStack.contains(TAG_COMPONENTS, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag components = serializedStack.getCompound(TAG_COMPONENTS);

        if (expectedKeys.contains(TAG_COLONY) && components.contains(COMPONENT_COLONY_ID, Tag.TAG_COMPOUND)) {
            CompoundTag colonyComponent = components.getCompound(COMPONENT_COLONY_ID);
            if (colonyComponent.contains(TAG_ID, Tag.TAG_INT)) {
                target.putInt(TAG_COLONY, colonyComponent.getInt(TAG_ID));
            }
            if (colonyComponent.contains(TAG_DIMENSION, Tag.TAG_STRING)) {
                target.putString(TAG_DIMENSION, colonyComponent.getString(TAG_DIMENSION));
            }
        }

        if (expectedKeys.contains(TAG_BUILDER) && components.contains(COMPONENT_BUILDING_ID, Tag.TAG_COMPOUND)) {
            CompoundTag buildingComponent = components.getCompound(COMPONENT_BUILDING_ID);
            int[] coordinates = buildingComponent.getIntArray(TAG_ID);
            if (coordinates.length >= 3) {
                CompoundTag builderTag = new CompoundTag();
                builderTag.putInt("x", coordinates[0]);
                builderTag.putInt("y", coordinates[1]);
                builderTag.putInt("z", coordinates[2]);
                target.put(TAG_BUILDER, builderTag);
            }
        }

        if (expectedKeys.contains(TAG_WAREHOUSE_SNAPSHOT) && components.contains(COMPONENT_WAREHOUSE_SNAPSHOT, Tag.TAG_COMPOUND)) {
            CompoundTag snapshotComponent = components.getCompound(COMPONENT_WAREHOUSE_SNAPSHOT);
            if (snapshotComponent.contains(TAG_SNAPSHOT, Tag.TAG_COMPOUND)) {
                target.put(TAG_WAREHOUSE_SNAPSHOT, snapshotComponent.getCompound(TAG_SNAPSHOT).copy());
            }
        }
    }

    private static void mergeRelevantData(CompoundTag target, CompoundTag candidate, Set<String> expectedKeys) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }

        CompoundTag relevant = findRelevantCompound(candidate, expectedKeys);
        if (relevant != null && !relevant.isEmpty()) {
            target.merge(relevant.copy());
        }
    }

    private static CompoundTag findRelevantCompound(CompoundTag candidate, Set<String> expectedKeys) {
        if (containsAnyKey(candidate, expectedKeys)) {
            return candidate;
        }

        for (String key : candidate.getAllKeys()) {
            Tag nestedTag = candidate.get(key);
            if (nestedTag instanceof CompoundTag nestedCompound) {
                CompoundTag nestedMatch = findRelevantCompound(nestedCompound, expectedKeys);
                if (nestedMatch != null && !nestedMatch.isEmpty()) {
                    return nestedMatch;
                }
            }
        }

        return null;
    }

    private static boolean containsAnyKey(CompoundTag candidate, Set<String> expectedKeys) {
        for (String key : expectedKeys) {
            if (candidate.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag extractWarehouseSnapshot(CompoundTag data) {
        return data.contains(TAG_WAREHOUSE_SNAPSHOT, Tag.TAG_COMPOUND) ? data.getCompound(TAG_WAREHOUSE_SNAPSHOT) : new CompoundTag();
    }

    private static BlockPos readBlockPos(CompoundTag data, String tagName) {
        if (!data.contains(tagName, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag positionTag = data.getCompound(tagName);
        return new BlockPos(positionTag.getInt("x"), positionTag.getInt("y"), positionTag.getInt("z"));
    }

    private static List<String> describeSnapshot(CompoundTag snapshot) {
        List<String> lines = new ArrayList<>();
        snapshot.getAllKeys().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(key -> lines.add("- " + formatResourceEntry(key, snapshot.get(key))));
        return lines;
    }

    private static String formatResourceEntry(String key, Tag value) {
        long count = extractNumber(value);
        return friendlyName(key) + (count > 0 ? " x" + count : "");
    }

    private static long extractNumber(Tag tag) {
        if (tag instanceof NumericTag numericTag) {
            return numericTag.getAsLong();
        }

        if (tag instanceof CompoundTag compoundTag) {
            if (compoundTag.contains("count", Tag.TAG_ANY_NUMERIC)) {
                return compoundTag.getLong("count");
            }
            if (compoundTag.contains("Count", Tag.TAG_ANY_NUMERIC)) {
                return compoundTag.getLong("Count");
            }
        }

        return 0L;
    }

    private static String friendlyName(String key) {
        String cleaned = stripSnapshotHash(key);
        if (cleaned.startsWith("item.") || cleaned.startsWith("block.")) {
            String translated = componentToString(Component.translatable(cleaned));
            if (!translated.equals(cleaned)) {
                return translated;
            }
        }

        if (cleaned.startsWith("item.") || cleaned.startsWith("block.")) {
            cleaned = cleaned.substring(cleaned.indexOf('.') + 1);
        }
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0) {
            cleaned = cleaned.substring(lastDot + 1);
        }
        cleaned = cleaned.replace('_', ' ');
        if (cleaned.isBlank()) {
            return "Unknown";
        }

        String[] parts = cleaned.split(" ");
        StringBuilder builder = new StringBuilder(cleaned.length());
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private static String stripSnapshotHash(String key) {
        String cleaned = key.replaceAll("-+\\d+$", "");
        while (cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String formatRequestLine(String requester, String shortText) {
        if (requester.isBlank()) {
            return "- " + shortText;
        }
        if (shortText.startsWith(requester)) {
            return "- " + shortText;
        }
        return "- " + requester + ": " + shortText;
    }

    private static String formatBlockPos(BlockPos blockPos) {
        return blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
    }

    private static String formatCompactBlockPos(BlockPos blockPos) {
        return blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
    }

    private static String normalizeTitle(String title, String fallback) {
        return title == null || title.isBlank() ? fallback : title;
    }

    private static String cleanText(String text) {
        return DisplayContentExtractor.clean(text == null ? "" : text).trim();
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static List<String> toPages(List<String> lines, int linesPerPage) {
        List<String> sanitized = new ArrayList<>(lines);
        while (!sanitized.isEmpty() && sanitized.getLast().isBlank()) {
            sanitized.removeLast();
        }

        if (sanitized.isEmpty()) {
            return List.of("");
        }

        List<String> pages = new ArrayList<>();
        for (int index = 0; index < sanitized.size(); index += linesPerPage) {
            int end = Math.min(sanitized.size(), index + linesPerPage);
            pages.add(String.join("\n", sanitized.subList(index, end)));
        }
        return pages;
    }

    private static String componentToString(Object object) {
        if (object instanceof Component component) {
            return DisplayContentExtractor.clean(component.getString());
        }
        return DisplayContentExtractor.clean(String.valueOf(object));
    }

    private static Collection<?> asCollection(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.values();
        }
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return List.of();
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private static Object invokeOptional(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return null;
        }

        try {
            return invoke(target, methodName, arguments);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, arguments);
        return method.invoke(target, arguments);
    }

    private static Method findMethod(Class<?> type, String methodName, Object[] arguments) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (matches(method, methodName, arguments)) {
                return method;
            }
        }

        throw new NoSuchMethodException(type.getName() + "#" + methodName);
    }

    private static boolean matches(Method method, String methodName, Object[] arguments) {
        if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!isCompatible(parameterTypes[index], arguments[index])) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCompatible(Class<?> parameterType, Object argument) {
        if (argument == null) {
            return !parameterType.isPrimitive();
        }

        Class<?> argumentType = argument.getClass();
        if (parameterType.isPrimitive()) {
            return PRIMITIVE_WRAPPERS.get(parameterType).isAssignableFrom(argumentType);
        }
        return parameterType.isAssignableFrom(argumentType);
    }

    private record ResourceLine(String name, int required, int onsite, long warehouse, int delivery, String status, int priority) {
        private String format(boolean includeWarehouse) {
            StringBuilder counts = new StringBuilder();
            counts.append(required).append('/').append(onsite);
            if (includeWarehouse && warehouse > 0) {
                counts.append(" w").append(warehouse);
            }
            if (delivery > 0) {
                counts.append(" d").append(delivery);
            }
            return counts + " " + name;
        }
    }
}