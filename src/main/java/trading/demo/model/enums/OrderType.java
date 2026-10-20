package trading.demo.model.enums;

public enum OrderType {
	// fills at once at the current quote
	MARKET,
	// waits as OPEN until the quote reaches limitPrice
	LIMIT
}
