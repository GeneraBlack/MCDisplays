package de.gener.mcdisplays.client.video;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.content.DisplayCluster;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class ClientVideoManager {
    private static final Map<Object, IVideoPlayer> ACTIVE_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<Object, String> ACTIVE_URLS = new ConcurrentHashMap<>();
    private static final Map<Object, AudioSourceInfo> AUDIO_SOURCES = new ConcurrentHashMap<>();
    private static final long INACTIVITY_TIMEOUT_MS = 20000L; // 20 seconds before releasing unrendered stream
    private static Boolean waterMediaInstalled = null;

    public static final class AudioSourceInfo {
        public BlockPos pos;
        public boolean isProjector;
        @Nullable
        public DisplayCluster.Cluster cluster;
        @Nullable
        public Direction facing;
        public int volume;
        public int distance;

        public AudioSourceInfo(BlockPos pos, boolean isProjector, @Nullable DisplayCluster.Cluster cluster, @Nullable Direction facing, int volume, int distance) {
            this.pos = pos;
            this.isProjector = isProjector;
            this.cluster = cluster;
            this.facing = facing;
            this.volume = volume;
            this.distance = distance;
        }
    }

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

        if (player != null && url.equals(currentUrl)) {
            player.markSeen();
            return player;
        }

        // Release old player if URL changed
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

    public static void updateAudioSource(Object key, BlockPos audioPos, boolean isProjector, @Nullable DisplayCluster.Cluster cluster, @Nullable Direction facing, int volume, int distance) {
        AudioSourceInfo info = AUDIO_SOURCES.get(key);
        if (info != null) {
            info.pos = audioPos;
            info.isProjector = isProjector;
            info.cluster = cluster;
            info.facing = facing;
            info.volume = volume;
            info.distance = distance;
        } else {
            AUDIO_SOURCES.put(key, new AudioSourceInfo(audioPos, isProjector, cluster, facing, volume, distance));
        }
    }

    public static void setLocalAudioSettings(Object key, BlockPos audioPos, boolean isProjector, int volume, int distance) {
        AudioSourceInfo info = AUDIO_SOURCES.get(key);
        if (info != null) {
            info.pos = audioPos;
            info.isProjector = isProjector;
            info.volume = volume;
            info.distance = distance;
        } else {
            AUDIO_SOURCES.put(key, new AudioSourceInfo(audioPos, isProjector, null, null, volume, distance));
        }

        IVideoPlayer player = ACTIVE_PLAYERS.get(key);
        if (player != null && player.isReady()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                double dist = Math.sqrt(mc.player.distanceToSqr(
                    audioPos.getX() + 0.5D,
                    audioPos.getY() + 0.5D,
                    audioPos.getZ() + 0.5D
                ));
                player.tickAudio(dist, volume, distance);
            }
        }
    }

    public static void stopPlayer(Object key) {
        ACTIVE_URLS.remove(key);
        AUDIO_SOURCES.remove(key);
        IVideoPlayer player = ACTIVE_PLAYERS.remove(key);
        if (player != null) {
            player.release();
        }
    }

    public static void tickClient() {
        if (ACTIVE_PLAYERS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Object, IVideoPlayer>> it = ACTIVE_PLAYERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, IVideoPlayer> entry = it.next();
            Object key = entry.getKey();
            IVideoPlayer player = entry.getValue();

            if (player == null || (now - player.getLastSeenTime() > INACTIVITY_TIMEOUT_MS)) {
                ACTIVE_URLS.remove(key);
                AUDIO_SOURCES.remove(key);
                if (player != null) {
                    player.release();
                }
                it.remove();
                continue;
            }

            // Continuous spatial audio ticking every client tick (independent of camera frustum):
            AudioSourceInfo audio = AUDIO_SOURCES.get(key);
            if (audio != null && player.isReady() && mc.player != null) {
                double dist;
                if (audio.isProjector || audio.cluster == null || audio.facing == null) {
                    dist = Math.sqrt(mc.player.distanceToSqr(
                        audio.pos.getX() + 0.5D,
                        audio.pos.getY() + 0.5D,
                        audio.pos.getZ() + 0.5D
                    ));
                } else {
                    dist = getDistanceToCluster(mc.player.position(), audio.pos, audio.cluster.width(), audio.cluster.height(), audio.facing);
                }
                player.tickAudio(dist, audio.volume, audio.distance);
            }
        }
    }

    public static void releaseAll() {
        ACTIVE_URLS.clear();
        AUDIO_SOURCES.clear();
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
