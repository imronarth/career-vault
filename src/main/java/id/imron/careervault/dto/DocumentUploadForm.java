package id.imron.careervault.dto;

import org.springframework.web.multipart.MultipartFile;

/** New document upload; the file is required. */
public class DocumentUploadForm extends DocumentMetadataForm {

    @RequiredFile
    private MultipartFile file;

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
