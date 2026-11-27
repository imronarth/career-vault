package id.imron.careervault.domain;

public enum DocumentCategory {

    CV("CV", "ti ti-file-cv", "bg-blue-lt"),
    COVER_LETTER("Cover Letter", "ti ti-mail", "bg-azure-lt"),
    PROJECT("Project", "ti ti-briefcase", "bg-indigo-lt"),
    CERTIFICATE("Certificate", "ti ti-certificate", "bg-yellow-lt"),
    IJAZAH("Ijazah", "ti ti-school", "bg-green-lt"),
    TRANSCRIPT("Transkrip Nilai", "ti ti-report", "bg-teal-lt"),
    PHOTO("Photo", "ti ti-photo", "bg-pink-lt"),
    ID_DOCUMENT("ID Document", "ti ti-id", "bg-orange-lt"),
    OTHER("Other", "ti ti-file", "bg-secondary-lt");

    private final String label;
    private final String iconClass;
    private final String badgeClass;

    DocumentCategory(String label, String iconClass, String badgeClass) {
        this.label = label;
        this.iconClass = iconClass;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getIconClass() {
        return iconClass;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
