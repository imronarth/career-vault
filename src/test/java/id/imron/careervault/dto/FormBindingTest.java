package id.imron.careervault.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import id.imron.careervault.domain.DocumentCategory;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.DataBinder;

class FormBindingTest {

    @Test
    void bindsRequestParametersUsingContractFieldNames() {
        DocumentUploadForm form = new DocumentUploadForm();
        DataBinder binder = new DataBinder(form);
        binder.setConversionService(new DefaultFormattingConversionService());

        binder.bind(new MutablePropertyValues(Map.of("title", "My CV", "category", "COVER_LETTER",
                "documentDate", "2026-03-09", "isPrimary", "true")));

        assertThat(binder.getBindingResult().hasErrors()).isFalse();
        assertThat(form.getCategory()).isEqualTo(DocumentCategory.COVER_LETTER);
        assertThat(form.getDocumentDate()).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(form.isIsPrimary()).isTrue();
        assertThat(binder.getBindingResult().getFieldValue("isPrimary")).isEqualTo("true");
    }

    @Test
    void uploadRequiresAFileButEditDoesNot() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            DocumentUploadForm upload = new DocumentUploadForm();
            upload.setTitle("My CV");
            upload.setCategory(DocumentCategory.CV);
            DocumentEditForm edit = new DocumentEditForm();
            edit.setTitle("My CV");
            edit.setCategory(DocumentCategory.CV);

            assertThat(factory.getValidator().validate(upload)).hasSize(1);
            upload.setFile(new MockMultipartFile("file", new byte[0]));
            assertThat(factory.getValidator().validate(upload)).hasSize(1);
            upload.setFile(new MockMultipartFile("file", new byte[] { 1 }));
            assertThat(factory.getValidator().validate(upload)).isEmpty();
            assertThat(factory.getValidator().validate(edit)).isEmpty();
        }
    }

    @Test
    void templatesCanReadDashboardRecordAsProperties() {
        DashboardSummaryDto summary = new DashboardSummaryDto(7, 3, 1, Map.of(), List.of(), List.of(), List.of(), 2,
                "1 KB", Map.of(), null);
        SpelExpressionParser parser = new SpelExpressionParser();

        assertThat(parser.parseExpression("totalJobs").getValue(summary)).isEqualTo(7L);
        assertThat(parser.parseExpression("totalStorageFormatted").getValue(summary)).isEqualTo("1 KB");
        assertThat(parser.parseExpression("primaryCv").getValue(summary)).isNull();
    }
}
