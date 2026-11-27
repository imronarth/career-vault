package id.imron.careervault.domain;

public enum JobStatus {

    SAVED("Saved", "bg-secondary-lt"),
    APPLIED("Applied", "bg-blue-lt"),
    HR_SCREEN("HR Screen", "bg-cyan-lt"),
    TECHNICAL_TEST("Technical Test", "bg-indigo-lt"),
    INTERVIEW("Interview", "bg-purple-lt"),
    OFFER("Offer", "bg-green-lt"),
    REJECTED("Rejected", "bg-red-lt"),
    WITHDRAWN("Withdrawn", "bg-orange-lt");

    private final String label;
    private final String badgeClass;

    JobStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /** True while the application is still in the pipeline. */
    public boolean isActive() {
        return switch (this) {
            case SAVED, APPLIED, HR_SCREEN, TECHNICAL_TEST, INTERVIEW -> true;
            case OFFER, REJECTED, WITHDRAWN -> false;
        };
    }
}
