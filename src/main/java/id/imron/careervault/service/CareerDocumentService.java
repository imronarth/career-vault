package id.imron.careervault.service;

import java.util.Objects;

import jakarta.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.dto.DocumentEditForm;
import id.imron.careervault.dto.DocumentMetadataForm;
import id.imron.careervault.dto.DocumentUploadForm;
import id.imron.careervault.repository.CareerDocumentRepository;
import id.imron.careervault.repository.CareerDocumentSpecs;
import id.imron.careervault.service.LocalFileStorageService.StoredFile;

/**
 * Documents and their files. Files cannot take part in a database transaction, so a new file is removed if the
 * transaction rolls back, and a replaced or deleted file is removed only after the commit.
 */
@Service
@Transactional
public class CareerDocumentService {

    private static final Logger log = LoggerFactory.getLogger(CareerDocumentService.class);

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final CareerDocumentRepository documents;

    private final LocalFileStorageService storage;

    public CareerDocumentService(CareerDocumentRepository documents, LocalFileStorageService storage) {
        this.documents = documents;
        this.storage = storage;
    }

    /** Newest first unless the pageable asks for another order. */
    @Transactional(readOnly = true)
    public Page<CareerDocument> search(String keyword, DocumentCategory category, Pageable pageable) {
        Pageable sorted = pageable.isPaged() && pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST)
                : pageable;
        return documents.findAll(CareerDocumentSpecs.matching(keyword, category), sorted);
    }

    /** @throws EntityNotFoundException if there is no such document */
    @Transactional(readOnly = true)
    public CareerDocument get(Long id) {
        return documents.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
    }

    /** @throws LocalFileStorageService.InvalidFileException if the file is rejected */
    public CareerDocument upload(DocumentUploadForm form) {
        CareerDocument document = new CareerDocument();
        applyMetadata(form, document);
        attachFile(document, form.getFile());
        unsetOtherPrimaries(document);
        return documents.saveAndFlush(document);
    }

    /**
     * Updates the metadata and, if a file was chosen, replaces the stored file.
     * @throws EntityNotFoundException if there is no such document
     * @throws LocalFileStorageService.InvalidFileException if the replacement file is rejected
     */
    public CareerDocument update(Long id, DocumentEditForm form) {
        CareerDocument document = get(id);
        applyMetadata(form, document);
        if (isChosen(form.getFile())) {
            String replaced = document.getStoredFilename();
            attachFile(document, form.getFile());
            deleteAfterCommit(replaced);
        }
        unsetOtherPrimaries(document);
        return documents.saveAndFlush(document);
    }

    /** @throws EntityNotFoundException if there is no such document */
    public void delete(Long id) {
        CareerDocument document = get(id);
        documents.delete(document);
        documents.flush();
        deleteAfterCommit(document.getStoredFilename());
    }

    /** @throws EntityNotFoundException if the document or its file is missing */
    @Transactional(readOnly = true)
    public DocumentDownload download(Long id) {
        CareerDocument document = get(id);
        return new DocumentDownload(document.getOriginalFilename(), document.getContentType(),
                storage.loadAsResource(document.getStoredFilename()));
    }

    private static void applyMetadata(DocumentMetadataForm form, CareerDocument document) {
        document.setTitle(form.getTitle().strip());
        document.setCategory(form.getCategory());
        document.setVersionLabel(blankToNull(form.getVersionLabel()));
        document.setIssuerOrContext(blankToNull(form.getIssuerOrContext()));
        document.setDocumentDate(form.getDocumentDate());
        document.setTags(blankToNull(form.getTags()));
        document.setDescription(blankToNull(form.getDescription()));
        document.setPrimary(form.isIsPrimary());
    }

    private void attachFile(CareerDocument document, MultipartFile file) {
        StoredFile stored = storage.store(file);
        deleteAfterRollback(stored.storedFilename());
        document.setStoredFilename(stored.storedFilename());
        document.setOriginalFilename(stored.originalFilename());
        document.setContentType(stored.contentType());
        document.setFileSizeBytes(stored.sizeBytes());
        document.setSha256Checksum(stored.sha256Checksum());
    }

    /** At most one document per category is primary. */
    private void unsetOtherPrimaries(CareerDocument document) {
        if (!document.isPrimary()) {
            return;
        }
        documents.findByCategoryAndPrimaryTrue(document.getCategory()).stream()
                .filter(other -> !Objects.equals(other.getId(), document.getId()))
                .forEach(other -> other.setPrimary(false));
    }

    /** An untouched file input arrives as an empty part without a name; a chosen empty file is still validated. */
    private static boolean isChosen(MultipartFile file) {
        return file != null && (!file.isEmpty() || StringUtils.hasText(file.getOriginalFilename()));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private void deleteAfterRollback(String storedFilename) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // With an unknown outcome the row may exist, so an orphan file is safer than a lost one.
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(storedFilename);
                }
            }
        });
    }

    private void deleteAfterCommit(String storedFilename) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(storedFilename);
            }
        });
    }

    /** The database outcome is already final here, so a failed file delete is only logged. */
    private void deleteQuietly(String storedFilename) {
        try {
            storage.delete(storedFilename);
        }
        catch (RuntimeException ex) {
            log.warn("Could not delete stored file {}", storedFilename, ex);
        }
    }

    /** What a controller needs to send a document to the browser. */
    public record DocumentDownload(String originalFilename, String contentType, Resource resource) {
    }
}
