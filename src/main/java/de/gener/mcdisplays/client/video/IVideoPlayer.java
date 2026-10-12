package de.gener.mcdisplays.client.video;

import net.minecraft.resources.ResourceLocation;

/**
 * Abstraction for an active video playback instance.
 */
public interface IVideoPlayer {
    void start();

    void pause();

    void resume();

    void stop();

    void seekTo(long ms);

    void setVolume(int volume);

    int getVolume();

    long getTime();

    long getDuration();

    boolean isPlaying();

    boolean isReady();

    boolean isEnded();

    boolean isBroken();

    int getWidth();

    int getHeight();

    int getTextureId();

    ResourceLocation getLocation();

    void tickAudio(double distanceToListener, int targetVolume, int maxDistance);

    void preRender();

    void markSeen();

    long getLastSeenTime();

    default String getStateName() {
        return "UNKNOWN";
    }

    default boolean isLoading() {
        return false;
    }

    default boolean isBuffering() {
        return false;
    }

    default String getFailureReason() {
        return "";
    }

    default void syncPlaybackTime(long startEpochMs) {
    }

    void release();
}
