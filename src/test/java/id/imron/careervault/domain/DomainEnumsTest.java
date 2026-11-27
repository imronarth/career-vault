package id.imron.careervault.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DomainEnumsTest {

    @Test
    void valuesMatchSpec() {
        assertThat(JobStatus.values()).extracting(Enum::name).containsExactly(
                "SAVED", "APPLIED", "HR_SCREEN", "TECHNICAL_TEST", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");
        assertThat(WorkMode.values()).extracting(Enum::name).containsExactly("REMOTE", "HYBRID", "ONSITE");
        assertThat(DocumentCategory.values()).extracting(Enum::name).containsExactly(
                "CV", "COVER_LETTER", "PROJECT", "CERTIFICATE", "IJAZAH", "TRANSCRIPT", "PHOTO", "ID_DOCUMENT",
                "OTHER");
    }

    @Test
    void onlyOfferRejectedAndWithdrawnAreInactive() {
        assertThat(Arrays.stream(JobStatus.values()).filter(status -> !status.isActive()))
                .containsExactly(JobStatus.OFFER, JobStatus.REJECTED, JobStatus.WITHDRAWN);
    }

    @Test
    void everyValueHasDisplayHelpers() {
        assertThat(JobStatus.values()).allSatisfy(status -> {
            assertThat(status.getLabel()).isNotBlank();
            assertThat(status.getBadgeClass()).matches("bg-[a-z]+-lt");
        });
        assertThat(WorkMode.values()).allSatisfy(mode -> assertThat(mode.getLabel()).isNotBlank());
        assertThat(DocumentCategory.values()).allSatisfy(category -> {
            assertThat(category.getLabel()).isNotBlank();
            assertThat(category.getIconClass()).startsWith("ti ti-");
            assertThat(category.getBadgeClass()).matches("bg-[a-z]+-lt");
        });
        assertThat(DocumentCategory.IJAZAH.getLabel()).isEqualTo("Ijazah");
        assertThat(DocumentCategory.TRANSCRIPT.getLabel()).isEqualTo("Transkrip Nilai");
    }
}
