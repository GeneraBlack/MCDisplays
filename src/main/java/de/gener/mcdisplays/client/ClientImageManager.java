package de.gener.mcdisplays.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.markdown.MediaPolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientImageManager {
    public enum Status {
        LOADING,
        READY,
        FAILED,
        BLOCKED
    }

    public record ImageInfo(ResourceLocation location, int width, int height, @Nullable DynamicTexture texture) {
        public float aspectRatio() {
            return height > 0 ? (float) width / (float) height : 1.0F;
        }
    }

    private static final class Entry {
        volatile Status status = Status.LOADING;
        volatile ImageInfo info;
        volatile String errorMessage;
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong VERSION = new AtomicLong(1);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "MCDisplays-ImageDownloader");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB max
    private static final int MAX_DIMENSION = 4096;

    private ClientImageManager() {
    }

    public static long getVersion() {
        return VERSION.get();
    }

    public static Status getStatus(String url) {
        if (url == null || url.isBlank()) {
            return Status.FAILED;
        }
        if (!MediaPolicy.isAllowed(url)) {
            return Status.BLOCKED;
        }
        Entry entry = CACHE.get(url);
        return entry != null ? entry.status : Status.LOADING;
    }

    @Nullable
    public static ImageInfo getImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (!MediaPolicy.isAllowed(url)) {
            return null;
        }

        Entry entry = CACHE.computeIfAbsent(url, u -> {
            Entry newEntry = new Entry();
            startDownload(u, newEntry);
            return newEntry;
        });

        if (entry.status == Status.READY) {
            return entry.info;
        }
        return null;
    }

    public static float getAspectRatio(String url) {
        if (url == null || url.isBlank()) {
            return 1.0F;
        }
        Entry entry = CACHE.get(url);
        if (entry != null && entry.info != null) {
            return entry.info.aspectRatio();
        }
        // Query to initiate download if not started yet
        getImage(url);
        return 1.0F;
    }

    private static void startDownload(String url, Entry entry) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = loadBytes(url);
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(bytes));
                int width = nativeImage.getWidth();
                int height = nativeImage.getHeight();

                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    nativeImage.close();
                    entry.status = Status.FAILED;
                    entry.errorMessage = "Image dimension exceeds limit (" + width + "x" + height + ")";
                    VERSION.incrementAndGet();
                    return;
                }

                String safeHash = hash(url);
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(McDisplaysMod.MODID, "remote_img_" + safeHash);
                        Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
                        entry.info = new ImageInfo(location, width, height, dynamicTexture);
                        entry.status = Status.READY;
                        VERSION.incrementAndGet();
                    } catch (Exception e) {
                        McDisplaysMod.LOGGER.error("Failed to register dynamic texture for {}", url, e);
                        entry.status = Status.FAILED;
                        nativeImage.close();
                        VERSION.incrementAndGet();
                    }
                });
            } catch (Exception e) {
                McDisplaysMod.LOGGER.warn("Failed to download or decode image from {}: {}", url, e.getMessage());
                entry.status = Status.FAILED;
                entry.errorMessage = e.getMessage();
                VERSION.incrementAndGet();
            }
        }, EXECUTOR);
    }

    private static byte[] loadBytes(String url) throws IOException, InterruptedException {
        Path cachePath = getDiskCachePath(url);
        if (cachePath != null && Files.exists(cachePath) && Files.size(cachePath) > 0) {
            try {
                return Files.readAllBytes(cachePath);
            } catch (Exception ignored) {
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 MCDisplays/1.0")
            .header("Accept", "image/png,image/jpeg,image/webp,image/*,*/*;q=0.8")
            .GET()
            .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

        byte[] body = response.body();
        if (body == null || body.length == 0) {
            throw new IOException("Empty response for " + url);
        }
        if (body.length > MAX_FILE_SIZE) {
            throw new IOException("Image file too large: " + body.length + " bytes (max " + MAX_FILE_SIZE + ")");
        }

        if (cachePath != null) {
            try {
                Files.createDirectories(cachePath.getParent());
                Files.write(cachePath, body);
            } catch (Exception ignored) {
            }
        }

        return body;
    }

    @Nullable
    private static Path getDiskCachePath(String url) {
        try {
            Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("mcdisplays_cache").resolve("images");
            return base.resolve(hash(url) + ".bin");
        } catch (Exception e) {
            return null;
        }
    }

    private static String hash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(16, hash.length); i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }
}
