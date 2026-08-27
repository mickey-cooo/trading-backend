package trading.demo.model.enums;

public enum UserType {
    TRADER("trader"),
    BROKER("broker"),
    DEALER("dealer");

    private final String value;

    UserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
