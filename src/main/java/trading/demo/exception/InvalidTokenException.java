package trading.demo.exception;

public class InvalidTokenException extends RuntimeException {
	public InvalidTokenException() {
		super("Invalid refresh token");
	}
}
