package id.imron.careervault.domain;

public enum WorkMode {

    REMOTE("Remote"),
    HYBRID("Hybrid"),
    ONSITE("On-site");

    private final String label;

    WorkMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
