package trading.demo.model.enums;

// Why money moved. Stored by name, never by position.
public enum LedgerType {
	// starting money
	FUNDING,
	// available -> reserved
	RESERVE,
	// reserved -> available
	RELEASE,
	// a filled order
	TRADE
}
