package de.gener.mcdisplays.client.video;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

public final class VideoTexture extends AbstractTexture {
    public VideoTexture(int glId) {
        this.id = glId;
    }

    public void updateId(int glId) {
        this.id = glId;
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // No-op: texture is allocated and updated via OpenGL by the video backend
    }

    @Override
    public void close() {
        // ID lifecycle is managed by the underlying VideoPlayer instance
    }
}
