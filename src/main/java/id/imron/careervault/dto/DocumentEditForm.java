package id.imron.careervault.dto;

import org.springframework.web.multipart.MultipartFile;

/** Document edit; the file is an optional replacement. */
public class DocumentEditForm extends DocumentMetadataForm {

    private MultipartFile file;

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
