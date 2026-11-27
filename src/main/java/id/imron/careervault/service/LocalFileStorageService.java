package id.imron.careervault.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded files flat under {@code app.storage.root}, named by a random UUID. Client file names are only kept
 * as display metadata and never become part of a path.
 */
@Service
public class LocalFileStorageService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private static final String GENERIC_CONTENT_TYPE = "application/octet-stream";

    /** Extension to accepted content types. The first entry is the one recorded for the stored file. */
    private static final Map<String, List<String>> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("pdf", List.of("application/pdf")),
            Map.entry("png", List.of("image/png")),
            Map.entry("jpg", List.of("image/jpeg", "image/pjpeg")),
            Map.entry("jpeg", List.of("image/jpeg", "image/pjpeg")),
            Map.entry("webp", List.of("image/webp")),
            Map.entry("doc", List.of("application/msword")),
            Map.entry("docx", List.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip")),
            Map.entry("xls", List.of("application/vnd.ms-excel")),
            Map.entry("xlsx", List.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip")),
            Map.entry("ppt", List.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", List.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/zip")),
            Map.entry("txt", List.of("text/plain")),
            Map.entry("md", List.of("text/markdown", "text/x-markdown", "text/plain")),
            Map.entry("zip", List.of("application/zip", "application/x-zip-compressed", "application/x-zip")));

    private final Path root;

    private final long maxFileSizeBytes;

    public LocalFileStorageService(@Value("${app.storage.root}") Path root,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.root = root.toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSize.toBytes();
        try {
            Files.createDirectories(this.root);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory " + this.root, ex);
        }
    }

    /**
     * Validates and saves an upload.
     * @throws InvalidFileException if the file is empty, too large, badly named or of a disallowed type
     */
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("The file is empty.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException("The file is larger than the allowed size.");
        }
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        String contentType = contentTypeFor(extension, file.getContentType());

        String storedFilename = UUID.randomUUID() + "." + extension;
        Path target = resolve(storedFilename);
        MessageDigest digest = sha256();
        long size;
        try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
            size = Files.copy(in, target);
        }
        catch (FileAlreadyExistsException ex) {
            throw new UncheckedIOException("Stored file name collision: " + storedFilename, ex);
        }
        catch (IOException ex) {
            deletePartialFile(target, ex);
            throw new UncheckedIOException("Failed to store file", ex);
        }
        return new StoredFile(storedFilename, originalFilename, contentType, size,
                HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * @throws InvalidFileException if the name points outside the storage directory
     * @throws EntityNotFoundException if there is no such stored file
     */
    public Resource loadAsResource(String storedFilename) {
        Path path = resolve(storedFilename);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new EntityNotFoundException("Stored file not found: " + storedFilename);
        }
        return new FileSystemResource(path);
    }

    /**
     * @return {@code true} if a file was removed, {@code false} if it did not exist
     * @throws InvalidFileException if the name points outside the storage directory
     */
    public boolean delete(String storedFilename) {
        Path path = resolve(storedFilename);
        try {
            return Files.deleteIfExists(path);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete stored file " + storedFilename, ex);
        }
    }

    private Path resolve(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            throw new InvalidFileException("Missing file name.");
        }
        Path path;
        try {
            path = root.resolve(storedFilename).normalize();
        }
        catch (InvalidPathException ex) {
            throw new InvalidFileException("Invalid file name.");
        }
        // Stored files are direct children of the root; this also excludes the root itself.
        if (!path.startsWith(root) || !root.equals(path.getParent())) {
            throw new InvalidFileException("Invalid file path.");
        }
        return path;
    }

    private static String safeOriginalFilename(String clientName) {
        if (clientName == null) {
            throw new InvalidFileException("Missing file name.");
        }
        String[] segments = clientName.replace('\\', '/').split("/");
        if (Arrays.asList(segments).contains("..")) {
            throw new InvalidFileException("Invalid file name.");
        }
        String name = segments.length == 0 ? "" : segments[segments.length - 1];
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            throw new InvalidFileException("Missing file name.");
        }
        return truncate(name);
    }

    /** Shortens a long name to fit the database column while keeping its extension. */
    private static String truncate(String name) {
        if (name.length() <= MAX_FILENAME_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String suffix = dot >= 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
        int end = MAX_FILENAME_LENGTH - suffix.length();
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end) + suffix;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.containsKey(extension)) {
            throw new InvalidFileException("This file type is not allowed.");
        }
        return extension;
    }

    private static String contentTypeFor(String extension, String declared) {
        List<String> accepted = ALLOWED_TYPES.get(extension);
        if (declared != null) {
            String type = declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (!type.isEmpty() && !type.equals(GENERIC_CONTENT_TYPE) && !accepted.contains(type)) {
                throw new InvalidFileException("The file content type does not match its extension.");
            }
        }
        return accepted.getFirst();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void deletePartialFile(Path target, IOException cause) {
        try {
            Files.deleteIfExists(target);
        }
        catch (IOException ex) {
            cause.addSuppressed(ex);
        }
    }

    /** Metadata of a saved upload, matching the file columns of {@code career_documents}. */
    public record StoredFile(String storedFilename, String originalFilename, String contentType, long sizeBytes,
            String sha256Checksum) {
    }

    /** A rejected upload or file name. The message is safe to show to the user. */
    public static class InvalidFileException extends IllegalArgumentException {

        public InvalidFileException(String message) {
            super(message);
        }
    }
}
