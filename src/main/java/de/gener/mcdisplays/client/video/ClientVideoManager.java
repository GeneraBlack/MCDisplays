package de.gener.mcdisplays.client.video;

import de.gener.mcdisplays.McDisplaysMod;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class ClientVideoManager {
    private static final Map<Object, IVideoPlayer> ACTIVE_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<Object, String> ACTIVE_URLS = new ConcurrentHashMap<>();
    private static final long INACTIVITY_TIMEOUT_MS = 20000L; // 20 seconds before releasing unrendered stream
    private static Boolean waterMediaInstalled = null;

    private ClientVideoManager() {
    }

    public static boolean isWaterMediaAvailable() {
        if (waterMediaInstalled == null) {
            waterMediaInstalled = ModList.get().isLoaded("watermedia");
            if (waterMediaInstalled) {
                McDisplaysMod.LOGGER.info("WATERMeDIA detected: video streaming and spatial cinema audio enabled");
            }
        }
        return waterMediaInstalled;
    }

    public static IVideoPlayer getOrCreate(Object key, String url, boolean loop) {
        if (url == null || url.isBlank() || !isWaterMediaAvailable()) {
            return null;
        }

        String currentUrl = ACTIVE_URLS.get(key);
        IVideoPlayer player = ACTIVE_PLAYERS.get(key);

        if (player != null && url.equals(currentUrl) && !player.isBroken()) {
            player.markSeen();
            return player;
        }

        // Release old player if URL changed or broken
        if (player != null) {
            stopPlayer(key);
        }

        try {
            String safeId = hash(key.toString() + "_" + url);
            IVideoPlayer newPlayer = WaterMediaCompat.create(url, loop, safeId);
            ACTIVE_PLAYERS.put(key, newPlayer);
            ACTIVE_URLS.put(key, url);
            return newPlayer;
        } catch (Throwable t) {
            McDisplaysMod.LOGGER.error("Failed to spawn video player for {}", url, t);
            return null;
        }
    }

    public static void stopPlayer(Object key) {
        ACTIVE_URLS.remove(key);
        IVideoPlayer player = ACTIVE_PLAYERS.remove(key);
        if (player != null) {
            player.release();
        }
    }

    public static void tickClient() {
        if (ACTIVE_PLAYERS.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Object, IVideoPlayer>> it = ACTIVE_PLAYERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, IVideoPlayer> entry = it.next();
            IVideoPlayer player = entry.getValue();
            if (player == null || (now - player.getLastSeenTime() > INACTIVITY_TIMEOUT_MS)) {
                ACTIVE_URLS.remove(entry.getKey());
                if (player != null) {
                    player.release();
                }
                it.remove();
            }
        }
    }

    public static void releaseAll() {
        ACTIVE_URLS.clear();
        for (IVideoPlayer player : ACTIVE_PLAYERS.values()) {
            if (player != null) {
                player.release();
            }
        }
        ACTIVE_PLAYERS.clear();
    }

    /**
     * Calculates the shortest 3D distance between a player and the planar rectangular surface of a display cluster.
     */
    public static double getDistanceToCluster(Vec3 playerPos, BlockPos origin, int width, int height, Direction facing) {
        double minX = origin.getX();
        double maxX = origin.getX() + 1.0;
        double minY = origin.getY();
        double maxY = origin.getY() + height;
        double minZ = origin.getZ();
        double maxZ = origin.getZ() + 1.0;

        Direction right = facing.getClockWise();
        if (right.getStepX() != 0) {
            if (right.getStepX() > 0) {
                maxX = origin.getX() + width;
            } else {
                minX = origin.getX() - width + 1.0;
            }
        }
        if (right.getStepZ() != 0) {
            if (right.getStepZ() > 0) {
                maxZ = origin.getZ() + width;
            } else {
                minZ = origin.getZ() - width + 1.0;
            }
        }

        double cx = Mth.clamp(playerPos.x, Math.min(minX, maxX), Math.max(minX, maxX));
        double cy = Mth.clamp(playerPos.y, minY, maxY);
        double cz = Mth.clamp(playerPos.z, Math.min(minZ, maxZ), Math.max(minZ, maxZ));

        double dx = playerPos.x - cx;
        double dy = playerPos.y - cy;
        double dz = playerPos.z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(12, hash.length); i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Isolated inner class to prevent JVM from attempting to load WaterMedia classes when not installed.
     */
    private static final class WaterMediaCompat {
        static IVideoPlayer create(String url, boolean loop, String safeId) {
            return new WaterMediaVideoPlayer(url, loop, safeId);
        }
    }
}
