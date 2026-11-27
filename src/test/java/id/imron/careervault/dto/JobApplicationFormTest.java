package id.imron.careervault.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.WorkMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JobApplicationFormTest {

    private static ValidatorFactory factory;

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsMinimalValidForm() {
        assertThat(invalidFields(validForm())).isEmpty();
    }

    @Test
    void rejectsBlankCompanyAndMissingStatus() {
        JobApplicationForm form = validForm();
        form.setCompanyName("   ");
        form.setStatus(null);

        assertThat(invalidFields(form)).containsExactlyInAnyOrder("companyName", "status");
    }

    @Test
    void rejectsMissingRoleAndWorkMode() {
        JobApplicationForm form = validForm();
        form.setRoleTitle(null);
        form.setWorkMode(null);

        assertThat(invalidFields(form)).containsExactlyInAnyOrder("roleTitle", "workMode");
    }

    @Test
    void rejectsTooLongValues() {
        JobApplicationForm form = validForm();
        form.setCompanyName("a".repeat(201));
        form.setNotes("a".repeat(10001));

        assertThat(invalidFields(form)).containsExactlyInAnyOrder("companyName", "notes");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "http://example.com", "HTTPS://example.com/jobs?id=1", " https://example.com " })
    void acceptsBlankOrWebUrls(String url) {
        JobApplicationForm form = validForm();
        form.setJobUrl(url);

        assertThat(invalidFields(form)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "javascript:alert(1)", "data:text/html,x", "ftp://example.com", "example.com",
            "https://", "https://a b" })
    void rejectsOtherUrls(String url) {
        JobApplicationForm form = validForm();
        form.setJobUrl(url);

        assertThat(invalidFields(form)).containsExactly("jobUrl");
    }

    private static JobApplicationForm validForm() {
        JobApplicationForm form = new JobApplicationForm();
        form.setCompanyName("Acme");
        form.setRoleTitle("Developer");
        form.setWorkMode(WorkMode.REMOTE);
        form.setStatus(JobStatus.APPLIED);
        return form;
    }

    private static Set<String> invalidFields(Object form) {
        return validator.validate(form).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
