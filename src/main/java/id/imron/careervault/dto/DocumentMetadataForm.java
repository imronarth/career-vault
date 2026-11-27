package id.imron.careervault.dto;

import java.time.LocalDate;

import id.imron.careervault.domain.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/** Metadata shared by the document upload and edit forms. */
public abstract class DocumentMetadataForm {

    @NotBlank(message = "Title is required.")
    @Size(max = 200, message = "Use at most 200 characters.")
    private String title;

    @NotNull(message = "Choose a category.")
    private DocumentCategory category;

    @Size(max = 50, message = "Use at most 50 characters.")
    private String versionLabel;

    @Size(max = 200, message = "Use at most 200 characters.")
    private String issuerOrContext;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate documentDate;

    @Size(max = 500, message = "Use at most 500 characters.")
    private String tags;

    @Size(max = 10000, message = "Use at most 10000 characters.")
    private String description;

    private boolean isPrimary;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public void setCategory(DocumentCategory category) {
        this.category = category;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public String getIssuerOrContext() {
        return issuerOrContext;
    }

    public void setIssuerOrContext(String issuerOrContext) {
        this.issuerOrContext = issuerOrContext;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
