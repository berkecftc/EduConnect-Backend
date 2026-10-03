package com.educonnect.postservice.model;

public enum ReportReason {
    BULLYING("Zorbalık veya hakaret", false),
    HARASSMENT("Taciz", true),
    THREAT("Şiddet veya tehdit", true),
    PERSONAL_DATA("Kişisel veri paylaşımı", false),
    ACADEMIC_INTEGRITY("Akademik dürüstlük ihlali", false),
    SPAM("Reklam veya spam", false),
    WRONG_CATEGORY("Yanlış kategori", false),
    OTHER("Diğer", false);

    private final String label;
    private final boolean sensitive;

    ReportReason(String label, boolean sensitive) {
        this.label = label;
        this.sensitive = sensitive;
    }

    public String label() {
        return label;
    }

    public boolean sensitive() {
        return sensitive;
    }
}
