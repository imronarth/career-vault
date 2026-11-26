# UI contract: controller ↔ Thymeleaf model attributes

Controllers (Claude) and templates (Gemini) MUST use exactly these names. If you change any of them, update this file in the same PR.

Base package: `id.imron.careervault`. Every page uses `templates/layout/app.html` through `th:replace="~{layout/app :: layout(title='…', active='dashboard|jobs|documents', content=~{::content})}"`.
Global (from `GlobalViewAdvice`, on every page): `currentUserFullName` (String). Flash: `successMessage`, `errorMessage` (String, optional).

| View | Route | Model attributes |
|---|---|---|
| `auth/login` | GET /login | `param.error`, `param.logout` (standard Spring Security) |
| `dashboard/index` | GET /dashboard | `summary` : `DashboardSummaryDto` |
| `jobs/list` | GET /jobs | `page` : `Page<JobApplication>`, `filter` : `JobSearchFilter`, `statuses` : `JobStatus[]`, `workModes` : `WorkMode[]` |
| `jobs/form` | GET /jobs/new, GET /jobs/{id}/edit | `form` : `JobApplicationForm`, `jobId` : Long or null, `cvDocuments` / `coverLetterDocuments` : `List<CareerDocument>`, `statuses`, `workModes` |
| `jobs/detail` | GET /jobs/{id} | `job` : `JobApplication`, `history` : `List<JobStatusHistory>`, `statusForm` : `JobStatusUpdateForm`, `statuses` |
| `documents/list` | GET /documents | `page` : `Page<CareerDocument>`, `categories` : `DocumentCategory[]`, `categoryCounts` : `Map<DocumentCategory,Long>`, `keyword` : String, `category` : DocumentCategory or null |
| `documents/form` | GET /documents/upload, GET /documents/{id}/edit | `form` : `DocumentUploadForm` or `DocumentEditForm`, `documentId` : Long or null, `categories` |
| `error/error` | any | `status` : int, `message` : String |

## DashboardSummaryDto fields
`totalJobs` (long), `activePipeline` (long), `offers` (long), `statusBreakdown` (`Map<JobStatus,Long>`), `upcomingFollowUps` (`List<JobApplication>`), `recentApplications` (`List<JobApplication>`), `recentDocuments` (`List<CareerDocument>`), `totalFiles` (long), `totalStorageFormatted` (String), `documentsPerCategory` (`Map<DocumentCategory,Long>`), `primaryCv` (`CareerDocument` or null)

## Enum helpers (templates may call these)
- `JobStatus.label`, `JobStatus.badgeClass` (e.g. `bg-blue-lt`)
- `WorkMode.label`
- `DocumentCategory.label`, `DocumentCategory.iconClass` (e.g. `ti ti-file-cv`), `DocumentCategory.badgeClass`

## Form field names
Same as the DTO field names in `docs/implementation_plan.md` §5. CSRF: every POST form includes `th:action`, so Thymeleaf adds the token automatically.
