package mr.foncier_api.enums;

public enum NiveauConfidentialite {
    PUBLIC("public"),
    RESTREINT("restreint"),
    CONFIDENTIEL("confidentiel");

    private final String code;

    NiveauConfidentialite(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
