package id.imron.careervault.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.WorkMode;
import id.imron.careervault.dto.JobApplicationForm;
import id.imron.careervault.dto.JobSearchFilter;
import id.imron.careervault.dto.JobStatusUpdateForm;
import id.imron.careervault.service.CareerDocumentService;
import id.imron.careervault.service.JobApplicationService;
import id.imron.careervault.service.JobApplicationService.InvalidDocumentLinkException;

/** Job tracker pages. Model attribute names are fixed by {@code docs/ui-contract.md}. */
@Controller
@RequestMapping("/jobs")
public class JobApplicationController {

    private static final int PAGE_SIZE = 20;

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final JobApplicationService jobs;

    private final CareerDocumentService documents;

    public JobApplicationController(JobApplicationService jobs, CareerDocumentService documents) {
        this.jobs = jobs;
        this.documents = documents;
    }

    @ModelAttribute("statuses")
    JobStatus[] statuses() {
        return JobStatus.values();
    }

    @ModelAttribute("workModes")
    WorkMode[] workModes() {
        return WorkMode.values();
    }

    // Only the page number comes from the request, so a client cannot choose the page size or sort property.
    @GetMapping
    String list(@ModelAttribute("filter") JobSearchFilter filter,
            @RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        model.addAttribute("page", jobs.search(filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        return "jobs/list";
    }

    @GetMapping("/export.csv")
    ResponseEntity<byte[]> export(@ModelAttribute("filter") JobSearchFilter filter) {
        ContentDisposition attachment = ContentDisposition.attachment()
                .filename("job-applications-" + LocalDate.now() + ".csv")
                .build();
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment.toString())
                .body(jobs.exportCsv(filter));
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute("form", new JobApplicationForm());
        return form(null, null, model);
    }

    @PostMapping
    String create(@Valid @ModelAttribute("form") JobApplicationForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                Long id = jobs.create(form).getId();
                redirect.addFlashAttribute("successMessage", "Job application created.");
                return "redirect:/jobs/" + id;
            }
            catch (InvalidDocumentLinkException ex) {
                result.rejectValue(ex.getField(), "invalidLink", ex.getMessage());
            }
        }
        return form(null, null, model);
    }

    @GetMapping("/{id}")
    String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("statusForm", new JobStatusUpdateForm());
        return detailPage(id, model);
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable("id") Long id, Model model) {
        JobApplication job = jobs.get(id);
        model.addAttribute("form", toForm(job));
        return form(id, job, model);
    }

    @PostMapping("/{id}")
    String update(@PathVariable("id") Long id, @Valid @ModelAttribute("form") JobApplicationForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                jobs.update(id, form);
                redirect.addFlashAttribute("successMessage", "Job application updated.");
                return "redirect:/jobs/" + id;
            }
            catch (InvalidDocumentLinkException ex) {
                result.rejectValue(ex.getField(), "invalidLink", ex.getMessage());
            }
        }
        return form(id, jobs.get(id), model);
    }

    @PostMapping("/{id}/status")
    String updateStatus(@PathVariable("id") Long id,
            @Valid @ModelAttribute("statusForm") JobStatusUpdateForm statusForm, BindingResult result, Model model,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return detailPage(id, model);
        }
        jobs.updateStatus(id, statusForm);
        redirect.addFlashAttribute("successMessage", "Status updated.");
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable("id") Long id, RedirectAttributes redirect) {
        jobs.delete(id);
        redirect.addFlashAttribute("successMessage", "Job application deleted.");
        return "redirect:/jobs";
    }

    private String detailPage(Long id, Model model) {
        model.addAttribute("job", jobs.get(id));
        model.addAttribute("history", jobs.history(id));
        return "jobs/detail";
    }

    private String form(Long jobId, JobApplication job, Model model) {
        model.addAttribute("jobId", jobId);
        model.addAttribute("cvDocuments",
                choices(DocumentCategory.CV, job == null ? null : job.getCvDocument()));
        model.addAttribute("coverLetterDocuments",
                choices(DocumentCategory.COVER_LETTER, job == null ? null : job.getCoverLetterDocument()));
        return "jobs/form";
    }

    /**
     * Documents of the category, newest first. The linked document stays selectable even if its category has changed
     * since, otherwise saving the form would silently remove the link.
     */
    private List<CareerDocument> choices(DocumentCategory category, CareerDocument linked) {
        Pageable all = Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        List<CareerDocument> choices = new ArrayList<>(documents.search(null, category, all).getContent());
        if (linked != null && choices.stream().noneMatch(choice -> choice.getId().equals(linked.getId()))) {
            choices.add(linked);
        }
        return choices;
    }

    private static JobApplicationForm toForm(JobApplication job) {
        JobApplicationForm form = new JobApplicationForm();
        form.setCompanyName(job.getCompanyName());
        form.setRoleTitle(job.getRoleTitle());
        form.setJobUrl(job.getJobUrl());
        form.setPlatform(job.getPlatform());
        form.setWorkMode(job.getWorkMode());
        form.setLocation(job.getLocation());
        form.setSalaryRange(job.getSalaryRange());
        form.setStatus(job.getStatus());
        form.setAppliedDate(job.getAppliedDate());
        form.setFollowUpDate(job.getFollowUpDate());
        form.setContactPerson(job.getContactPerson());
        form.setNotes(job.getNotes());
        form.setCvDocumentId(job.getCvDocument() == null ? null : job.getCvDocument().getId());
        form.setCoverLetterDocumentId(
                job.getCoverLetterDocument() == null ? null : job.getCoverLetterDocument().getId());
        return form;
    }
}
