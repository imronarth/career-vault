package id.imron.careervault.controller;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.dto.DocumentEditForm;
import id.imron.careervault.dto.DocumentUploadForm;
import id.imron.careervault.service.CareerDocumentService;
import id.imron.careervault.service.CareerDocumentService.DocumentDownload;
import id.imron.careervault.service.LocalFileStorageService.InvalidFileException;

/** Document vault pages and file delivery. Model attribute names are fixed by {@code docs/ui-contract.md}. */
@Controller
@RequestMapping("/documents")
public class CareerDocumentController {

    private static final int PAGE_SIZE = 12;

    /** Types a browser can show without running anything from the file. */
    private static final Set<String> PREVIEWABLE_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/webp");

    private final CareerDocumentService documents;

    public CareerDocumentController(CareerDocumentService documents) {
        this.documents = documents;
    }

    @ModelAttribute("categories")
    DocumentCategory[] categories() {
        return DocumentCategory.values();
    }

    // Only the page number comes from the request, so a client cannot choose the page size or sort property.
    @GetMapping
    String list(@RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) DocumentCategory category,
            @RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        model.addAttribute("page",
                documents.search(keyword, category, PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        model.addAttribute("categoryCounts", documents.countByCategory());
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        return "documents/list";
    }

    @GetMapping("/upload")
    String uploadForm(Model model) {
        model.addAttribute("form", new DocumentUploadForm());
        return form(null, model);
    }

    @PostMapping("/upload")
    String upload(@Valid @ModelAttribute("form") DocumentUploadForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                documents.upload(form);
                redirect.addFlashAttribute("successMessage", "Document uploaded.");
                return "redirect:/documents";
            }
            catch (InvalidFileException ex) {
                result.rejectValue("file", "invalidFile", ex.getMessage());
            }
        }
        return form(null, model);
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("form", toForm(documents.get(id)));
        return form(id, model);
    }

    @PostMapping("/{id}/edit")
    String update(@PathVariable("id") Long id, @Valid @ModelAttribute("form") DocumentEditForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                documents.update(id, form);
                redirect.addFlashAttribute("successMessage", "Document updated.");
                return "redirect:/documents";
            }
            catch (InvalidFileException ex) {
                result.rejectValue("file", "invalidFile", ex.getMessage());
            }
        }
        return form(id, model);
    }

    @GetMapping("/{id}/download")
    ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        DocumentDownload download = documents.download(id);
        return file(download, "attachment");
    }

    /** Shows PDFs and images in the browser; anything else is sent to the download route. */
    @GetMapping("/{id}/preview")
    ResponseEntity<Resource> preview(@PathVariable("id") Long id) {
        DocumentDownload download = documents.download(id);
        if (!PREVIEWABLE_TYPES.contains(download.contentType())) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/documents/" + id + "/download")
                    .build();
        }
        return file(download, "inline");
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable("id") Long id, RedirectAttributes redirect) {
        documents.delete(id);
        redirect.addFlashAttribute("successMessage", "Document deleted.");
        return "redirect:/documents";
    }

    private static ResponseEntity<Resource> file(DocumentDownload download, String disposition) {
        // RFC 5987 encoding keeps any file name intact and cannot break out of the header.
        String filename = UriUtils.encode(download.originalFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + filename)
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    private static String form(Long documentId, Model model) {
        model.addAttribute("documentId", documentId);
        return "documents/form";
    }

    private static DocumentEditForm toForm(CareerDocument document) {
        DocumentEditForm form = new DocumentEditForm();
        form.setTitle(document.getTitle());
        form.setCategory(document.getCategory());
        form.setVersionLabel(document.getVersionLabel());
        form.setIssuerOrContext(document.getIssuerOrContext());
        form.setDocumentDate(document.getDocumentDate());
        form.setTags(document.getTags());
        form.setDescription(document.getDescription());
        form.setIsPrimary(document.isPrimary());
        return form;
    }
}
