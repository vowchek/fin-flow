package com.ledger.infrastructure.storage;

import com.ledger.domain.AssetMarket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LogoStorageService {

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");

    private final Path root;

    public LogoStorageService(@Value("${ledger.storage.logos-dir:./data/logos}") String logosDir) throws IOException {
        this.root = Path.of(logosDir).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("stock"));
        Files.createDirectories(root.resolve("crypto"));
    }

    public String store(AssetMarket market, UUID instrumentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported logo type: " + contentType);
        }
        String ext = extension(contentType, file.getOriginalFilename());
        String filename = instrumentId + ext;
        Path dir = root.resolve(folder(market));
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return filename;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store logo", ex);
        }
    }

    public Path resolve(AssetMarket market, String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid logo filename");
        }
        return root.resolve(folder(market)).resolve(filename).normalize();
    }

    public void deleteIfExists(AssetMarket market, String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(market, filename));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public static String publicUrl(AssetMarket market, UUID id) {
        String kind = market == AssetMarket.CRYPTO ? "crypto" : "stock";
        return "/api/v1/media/" + kind + "-instruments/" + id + "/logo";
    }

    private static String folder(AssetMarket market) {
        return market == AssetMarket.CRYPTO ? "crypto" : "stock";
    }

    private static String extension(String contentType, String original) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> {
                if (original != null && original.contains(".")) {
                    yield original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                }
                yield ".bin";
            }
        };
    }
}
