package trading.demo.model.enums;

public enum Common {
	ACTIVE("active"), PENDING("pending"), SUSPENDED("suspended"), DELETED("deleted");

	private final String value;

	Common(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

}
