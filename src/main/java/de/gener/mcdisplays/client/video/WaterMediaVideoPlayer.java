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
    private boolean failed;
    private String failureReason = "";
    private boolean hasEverDecodedFrame;
    private final long creationTime;
    private String lastLoggedState = "";
    private int lastVolume = -1;
    private boolean initialSynced;
    private long lastSeekTime;

    public WaterMediaVideoPlayer(String url, boolean loop, String safeId) {
        this.url = url;
        this.loop = loop;
        this.safeId = safeId;
        this.location = ResourceLocation.fromNamespaceAndPath(McDisplaysMod.MODID, "vtex_" + safeId);
        this.lastSeenTime = System.currentTimeMillis();
        this.creationTime = System.currentTimeMillis();

        McDisplaysMod.LOGGER.info("Created WaterMediaVideoPlayer [{}] for URL: {}", safeId, url);
        try {
            this.player = new VideoPlayer(Minecraft.getInstance());
            this.texture = new VideoTexture(this.player.texture());
            Minecraft.getInstance().getTextureManager().register(this.location, this.texture);
            this.player.setRepeatMode(loop);
            start();
        } catch (Throwable t) {
            McDisplaysMod.LOGGER.error("Failed to initialize WaterMedia VideoPlayer for {}", url, t);
            markFailed("Initialisierung der Video-Engine fehlgeschlagen");
        }
    }

    @Override
    public void start() {
        if (player != null && !started && !released && !failed) {
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
                markFailed("Start fehlgeschlagen: " + t.getMessage());
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
        return player != null && !released && !failed && player.isPlaying();
    }

    @Override
    public boolean isReady() {
        if (player == null || released || failed) {
            return false;
        }
        if (player.isBroken() || player.isEnded()) {
            return false;
        }
        String state = getStateName();
        if ("OPENING".equalsIgnoreCase(state) || "NOTHING_SPECIAL".equalsIgnoreCase(state) ||
            "STOPPED".equalsIgnoreCase(state) || "ERROR".equalsIgnoreCase(state) || "ENDED".equalsIgnoreCase(state)) {
            return false;
        }
        return player.isReady() && player.width() > 1 && player.height() > 1 && player.texture() > 0;
    }

    @Override
    public boolean isEnded() {
        return player != null && !released && player.isEnded();
    }

    @Override
    public boolean isBroken() {
        return player == null || released || failed || player.isBroken();
    }

    @Override
    public String getFailureReason() {
        return failureReason;
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
        if (player == null || released || failed) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            if (lastVolume != 0) {
                player.setVolume(0);
                lastVolume = 0;
            }
            return;
        }

        if (distanceToListener >= maxDistance || maxDistance <= 0) {
            if (lastVolume != 0) {
                player.setVolume(0);
                lastVolume = 0;
            }
            return;
        }

        // Linear distance falloff (1.0 at origin down to 0.0 at maxDistance)
        float distFactor = 1.0F - Mth.clamp((float) (distanceToListener / maxDistance), 0.0F, 1.0F);

        float mcMaster = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        float mcRecords = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        float soundScale = mcMaster * (mcRecords > 0.0F ? mcRecords : 1.0F);

        int finalVolume = Mth.clamp(Math.round(targetVolume * distFactor * soundScale), 0, 100);
        if (Math.abs(finalVolume - lastVolume) >= 1 || (finalVolume == 0 && lastVolume != 0)) {
            player.setVolume(finalVolume);
            lastVolume = finalVolume;
        }
    }

    @Override
    public void syncPlaybackTime(long startEpochMs) {
        if (startEpochMs <= 0L || player == null || released || failed || !isReady()) {
            return;
        }

        long duration = getDuration();
        // Live streams (Twitch/Kick/HLS live) typically have unknown duration (<= 0) - don't seek live streams
        if (duration <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsedMs = now - startEpochMs;
        if (elapsedMs < 0) {
            elapsedMs = 0;
        }

        if (loop) {
            elapsedMs = elapsedMs % duration;
        } else if (elapsedMs >= duration) {
            // Video has ended in real-time
            return;
        }

        long currentTime = getTime();
        long diff = Math.abs(currentTime - elapsedMs);

        if (!initialSynced) {
            // First time player becomes ready: if late by more than 1 second, jump immediately to current timestamp
            if (elapsedMs > 1000L && diff > 1500L) {
                McDisplaysMod.LOGGER.info("WaterMedia [{}] initial sync seek to {} ms (was {} ms, diff {} ms)",
                    safeId, elapsedMs, currentTime, diff);
                player.seekTo(elapsedMs);
                lastSeekTime = now;
            }
            initialSynced = true;
        } else {
            // Continuous resync: if local playback drifted by more than 3 seconds (buffering or lag), catch up
            if (diff > 3000L && (now - lastSeekTime > 5000L)) {
                McDisplaysMod.LOGGER.info("WaterMedia [{}] resyncing playback drift: seek to {} ms (was {} ms, diff {} ms)",
                    safeId, elapsedMs, currentTime, diff);
                player.seekTo(elapsedMs);
                lastSeekTime = now;
            }
        }
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

            if (player.width() > 1 && player.height() > 1) {
                hasEverDecodedFrame = true;
            }

            String current = getStateName();
            if (!current.equals(lastLoggedState)) {
                lastLoggedState = current;
                McDisplaysMod.LOGGER.info("WaterMedia [{}] state changed -> {} (ready={}, broken={}, time={}/{}ms, dim={}x{})",
                    safeId, current, isReady(), isBroken(), getTime(), getDuration(), getWidth(), getHeight());
            }

            // Failure detection
            if (!failed && player.isBroken()) {
                markFailed("Videoplayer meldet internen Fehler (isBroken=true)");
            } else if (!failed && player.isEnded() && !hasEverDecodedFrame) {
                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    markFailed("YouTube-Stream erfordert das WATERMeDIA-YouTube Addon");
                } else {
                    markFailed("Stream konnte nicht decodiert werden (Format / URL ungültig)");
                }
            } else if (!failed && !hasEverDecodedFrame && (System.currentTimeMillis() - creationTime > 15000L)) {
                markFailed("Zeitüberschreitung beim Laden des Streams (Timeout nach 15s)");
            }
        }
    }

    private void markFailed(String reason) {
        this.failed = true;
        this.failureReason = reason;
        McDisplaysMod.LOGGER.warn("WaterMedia [{}] playback failure: {}", safeId, reason);
        if (player != null) {
            try {
                player.setRepeatMode(false);
                player.stop();
            } catch (Throwable ignored) {
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
