package trading.demo.exception;

public class InsufficientFundsException extends RuntimeException {
	public InsufficientFundsException() {
		super("Insufficient funds");
	}
}
