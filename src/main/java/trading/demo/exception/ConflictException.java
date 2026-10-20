package trading.demo.exception;

// The request is fine but clashes with the current state; shown to the caller as 409.
public class ConflictException extends RuntimeException {
	public ConflictException(String message) {
		super(message);
	}
}
