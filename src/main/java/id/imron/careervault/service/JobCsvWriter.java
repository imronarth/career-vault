package id.imron.careervault.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.JobApplication;

/** RFC 4180 CSV with a byte order mark, so that spreadsheet programs detect UTF-8. */
final class JobCsvWriter {

    private static final String BYTE_ORDER_MARK = "﻿";

    private static final String LINE_END = "\r\n";

    /** A cell starting with one of these may be run as a formula by a spreadsheet. */
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private static final List<String> HEADER = List.of("Company", "Role", "Status", "Work Mode", "Platform",
            "Location", "Salary Range", "Applied Date", "Follow-up Date", "Contact Person", "Job URL", "CV",
            "Cover Letter", "Notes", "Created At", "Updated At");

    private JobCsvWriter() {
    }

    static byte[] write(List<JobApplication> jobs) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK);
        appendRow(csv, HEADER.stream());
        for (JobApplication job : jobs) {
            appendRow(csv, Stream.of(
                    job.getCompanyName(),
                    job.getRoleTitle(),
                    job.getStatus().getLabel(),
                    job.getWorkMode().getLabel(),
                    job.getPlatform(),
                    job.getLocation(),
                    job.getSalaryRange(),
                    job.getAppliedDate(),
                    job.getFollowUpDate(),
                    job.getContactPerson(),
                    job.getJobUrl(),
                    title(job.getCvDocument()),
                    title(job.getCoverLetterDocument()),
                    job.getNotes(),
                    job.getCreatedAt(),
                    job.getUpdatedAt()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String title(CareerDocument document) {
        return document == null ? null : document.getTitle();
    }

    private static void appendRow(StringBuilder csv, Stream<?> values) {
        csv.append(values.map(JobCsvWriter::cell).collect(Collectors.joining(","))).append(LINE_END);
    }

    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && FORMULA_STARTS.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.chars().anyMatch(c -> c == ',' || c == '"' || c == '\r' || c == '\n')) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
