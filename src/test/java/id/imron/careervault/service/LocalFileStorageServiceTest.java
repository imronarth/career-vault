package id.imron.careervault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import id.imron.careervault.service.LocalFileStorageService.InvalidFileException;
import id.imron.careervault.service.LocalFileStorageService.StoredFile;
import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private Path root;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("storage");
        service = new LocalFileStorageService(root, DataSize.ofBytes(1024));
    }

    @Test
    void storesUnderUuidNameAndKeepsOriginalName() {
        StoredFile first = service.store(upload("My CV.PDF", "application/pdf", "abc"));
        StoredFile second = service.store(upload("My CV.PDF", "application/pdf", "abc"));

        assertThat(first.storedFilename())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf")
                .isNotEqualTo(second.storedFilename());
        assertThat(first.originalFilename()).isEqualTo("My CV.PDF");
        assertThat(root.resolve(first.storedFilename())).hasContent("abc");
    }

    @Test
    void computesSizeAndSha256() {
        StoredFile stored = service.store(upload("a.txt", "text/plain", "abc"));

        assertThat(stored.sizeBytes()).isEqualTo(3);
        assertThat(stored.sha256Checksum())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @ParameterizedTest
    @CsvSource({ "a.pdf,application/pdf", "a.png,image/png", "a.jpg,image/jpeg", "a.jpeg,image/jpeg",
            "a.webp,image/webp", "a.doc,application/msword",
            "a.docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "a.xls,application/vnd.ms-excel",
            "a.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "a.ppt,application/vnd.ms-powerpoint",
            "a.pptx,application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "a.txt,text/plain", "a.md,text/markdown", "a.zip,application/zip" })
    void acceptsEveryAllowedType(String name, String contentType) {
        assertThat(service.store(upload(name, contentType, "x")).contentType()).isEqualTo(contentType);
    }

    @Test
    void derivesContentTypeFromExtensionWhenClientSendsNoneOrGeneric() {
        assertThat(service.store(upload("a.pdf", null, "x")).contentType()).isEqualTo("application/pdf");
        assertThat(service.store(upload("a.md", "application/octet-stream", "x")).contentType())
                .isEqualTo("text/markdown");
        assertThat(service.store(upload("a.txt", "Text/Plain; charset=UTF-8", "x")).contentType())
                .isEqualTo("text/plain");
    }

    @ParameterizedTest
    @CsvSource({ "a.exe,application/octet-stream", "a.html,text/html", "a.svg,image/svg+xml", "a,text/plain",
            "a.pdf.sh,application/pdf", "a.pdf,text/html", "a.png,application/pdf" })
    void rejectsDisallowedTypes(String name, String contentType) {
        assertThatThrownBy(() -> service.store(upload(name, contentType, "x")))
                .isInstanceOf(InvalidFileException.class);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> service.store(upload("a.txt", "text/plain", "")))
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> service.store(upload("a.txt", "text/plain", "x".repeat(1025))))
                .isInstanceOf(InvalidFileException.class);
        assertThat(storedFileCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "../evil.pdf", "..\\evil.pdf", "docs/../../evil.pdf", "..", "/" })
    void rejectsTraversalInUploadName(String name) {
        assertThatThrownBy(() -> service.store(upload(name, "application/pdf", "x")))
                .isInstanceOf(InvalidFileException.class);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void stripsDirectoriesAndControlCharactersFromOriginalName() {
        assertThat(service.store(upload("C:\\Users\\me\\cv\r\n.pdf", "application/pdf", "x")).originalFilename())
                .isEqualTo("cv.pdf");
    }

    @Test
    void shortensLongOriginalNameKeepingExtension() {
        String name = service.store(upload("a".repeat(300) + ".pdf", "application/pdf", "x")).originalFilename();

        assertThat(name).hasSize(255).endsWith(".pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = { "../secret.txt", "a/../../secret.txt", "/etc/passwd", "sub/file.txt", ".", "..", " " })
    void rejectsPathsOutsideRootOnLoadAndDelete(String name) throws IOException {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> service.loadAsResource(name)).isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> service.delete(name)).isInstanceOf(InvalidFileException.class);
        assertThat(secret).exists();
        assertThat(root).exists();
    }

    @Test
    void loadsStoredFileAsResource() throws IOException {
        StoredFile stored = service.store(upload("a.txt", "text/plain", "hello"));

        assertThat(service.loadAsResource(stored.storedFilename()).getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("hello");
    }

    @Test
    void doesNotLoadMissingFilesOrSymlinks() throws IOException {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(root.resolve("link.txt"), secret);

        assertThatThrownBy(() -> service.loadAsResource("missing.txt")).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.loadAsResource("link.txt")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deletesStoredFile() {
        StoredFile stored = service.store(upload("a.txt", "text/plain", "x"));

        assertThat(service.delete(stored.storedFilename())).isTrue();
        assertThat(root.resolve(stored.storedFilename())).doesNotExist();
        assertThat(service.delete(stored.storedFilename())).isFalse();
    }

    @Test
    void removesPartialFileWhenReadingTheUploadFails() {
        MockMultipartFile failing = new MockMultipartFile("file", "a.txt", "text/plain", new byte[] { 1 }) {
            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    private int reads;

                    @Override
                    public int read() throws IOException {
                        if (reads++ == 0) {
                            return 'x';
                        }
                        throw new IOException("broken upload");
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        buffer[offset] = (byte) read();
                        return 1;
                    }
                };
            }
        };

        assertThatThrownBy(() -> service.store(failing)).isInstanceOf(UncheckedIOException.class);
        assertThat(storedFileCount()).isZero();
    }

    private static MockMultipartFile upload(String name, String contentType, String content) {
        return new MockMultipartFile("file", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private long storedFileCount() {
        try (Stream<Path> files = Files.list(root)) {
            return files.count();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
