package de.gener.mcdisplays.client.video;

import de.gener.mcdisplays.McDisplaysMod;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.watermedia.api.player.videolan.VideoPlayer;

public final class WaterMediaVideoPlayer implements IVideoPlayer {
    private final String url;
    private final boolean loop;
    private final String safeId;
    private final ResourceLocation location;
    private VideoPlayer player;
    private VideoTexture texture;
    private long lastSeenTime;
    private boolean started;
    private boolean released;
    private String lastLoggedState = "";

    public WaterMediaVideoPlayer(String url, boolean loop, String safeId) {
        this.url = url;
        this.loop = loop;
        this.safeId = safeId;
        this.location = ResourceLocation.fromNamespaceAndPath(McDisplaysMod.MODID, "vtex_" + safeId);
        this.lastSeenTime = System.currentTimeMillis();

        McDisplaysMod.LOGGER.info("Created WaterMediaVideoPlayer [{}] for URL: {}", safeId, url);
        try {
            this.player = new VideoPlayer(Minecraft.getInstance());
            this.texture = new VideoTexture(this.player.texture());
            Minecraft.getInstance().getTextureManager().register(this.location, this.texture);
            this.player.setRepeatMode(loop);
            start();
        } catch (Throwable t) {
            McDisplaysMod.LOGGER.error("Failed to initialize WaterMedia VideoPlayer for {}", url, t);
        }
    }

    @Override
    public void start() {
        if (player != null && !started && !released) {
            try {
                URI uri;
                try {
                    uri = org.watermedia.api.network.NetworkAPI.parseURI(url);
                } catch (Throwable ignored) {
                    uri = URI.create(url);
                }
                McDisplaysMod.LOGGER.info("Starting WaterMedia playback [{}] URI: {}", safeId, uri);
                player.start(uri);
                player.setRepeatMode(loop);
                started = true;
            } catch (Throwable t) {
                McDisplaysMod.LOGGER.error("Failed to start WaterMedia playback for {}", url, t);
            }
        }
    }

    @Override
    public void pause() {
        if (player != null && !released && player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    public void resume() {
        if (player != null && !released && player.isPaused()) {
            player.resume();
        }
    }

    @Override
    public void stop() {
        if (player != null && !released) {
            player.stop();
        }
    }

    @Override
    public void seekTo(long ms) {
        if (player != null && !released) {
            player.seekTo(ms);
        }
    }

    @Override
    public void setVolume(int volume) {
        if (player != null && !released) {
            player.setVolume(Mth.clamp(volume, 0, 100));
        }
    }

    @Override
    public int getVolume() {
        return player != null && !released ? player.getVolume() : 0;
    }

    @Override
    public long getTime() {
        return player != null && !released ? player.getTime() : 0;
    }

    @Override
    public long getDuration() {
        return player != null && !released ? player.getDuration() : 0;
    }

    @Override
    public boolean isPlaying() {
        return player != null && !released && player.isPlaying();
    }

    @Override
    public boolean isReady() {
        return player != null && !released && player.isReady();
    }

    @Override
    public boolean isEnded() {
        return player != null && !released && player.isEnded();
    }

    @Override
    public boolean isBroken() {
        return player == null || released || player.isBroken();
    }

    @Override
    public int getWidth() {
        return player != null && !released ? Math.max(1, player.width()) : 1920;
    }

    @Override
    public int getHeight() {
        return player != null && !released ? Math.max(1, player.height()) : 1080;
    }

    @Override
    public int getTextureId() {
        return player != null && !released ? player.texture() : 0;
    }

    @Override
    public ResourceLocation getLocation() {
        return location;
    }

    @Override
    public void tickAudio(double distanceToListener, int targetVolume, int maxDistance) {
        if (player == null || released) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            player.setVolume(0);
            return;
        }

        if (distanceToListener >= maxDistance || maxDistance <= 0) {
            player.setVolume(0);
            return;
        }

        float distRatio = (float) (distanceToListener / maxDistance);
        float distFactor = 1.0F - Mth.clamp(distRatio, 0.0F, 1.0F);
        distFactor = distFactor * distFactor;

        float mcMaster = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        float mcRecords = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        float overallFactor = distFactor * mcMaster * mcRecords;

        int finalVolume = Math.round(targetVolume * overallFactor);
        player.setVolume(Mth.clamp(finalVolume, 0, 100));
    }

    @Override
    public String getStateName() {
        return player != null && !released ? player.getStateName() : "RELEASED";
    }

    @Override
    public boolean isLoading() {
        return player != null && !released && player.isLoading();
    }

    @Override
    public boolean isBuffering() {
        return player != null && !released && player.isBuffering();
    }

    @Override
    @SuppressWarnings("removal")
    public void preRender() {
        if (player != null && !released) {
            player.preRender();
            if (texture != null) {
                texture.updateId(player.texture());
            }
            String current = getStateName();
            if (!current.equals(lastLoggedState)) {
                lastLoggedState = current;
                McDisplaysMod.LOGGER.info("WaterMedia [{}] state changed -> {} (ready={}, broken={}, time={}/{}ms)",
                    safeId, current, isReady(), isBroken(), getTime(), getDuration());
            }
        }
    }

    @Override
    public void markSeen() {
        this.lastSeenTime = System.currentTimeMillis();
    }

    @Override
    public long getLastSeenTime() {
        return lastSeenTime;
    }

    @Override
    public void release() {
        if (!released) {
            released = true;
            McDisplaysMod.LOGGER.info("Releasing WaterMediaVideoPlayer [{}] for URL: {}", safeId, url);
            if (player != null) {
                try {
                    player.stop();
                    player.release();
                } catch (Throwable t) {
                    McDisplaysMod.LOGGER.warn("Error while releasing VideoPlayer for {}", url, t);
                }
                player = null;
            }
            try {
                Minecraft.getInstance().getTextureManager().release(this.location);
            } catch (Throwable ignored) {
            }
        }
    }
}
