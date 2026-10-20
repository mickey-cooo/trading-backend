package trading.demo.config;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

// Argon2 is memory-hungry and login/register are public, so cap how many run at once; extra callers wait, then get 503.
public class BoundedPasswordEncoder implements PasswordEncoder {
	private static final long WAIT_SECONDS = 2;

	private final PasswordEncoder delegate;
	private final Semaphore permits;

	public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrent) {
		this.delegate = delegate;
		this.permits = new Semaphore(maxConcurrent, true);
	}

	@Override
	public String encode(CharSequence rawPassword) {
		acquire();
		try {
			return delegate.encode(rawPassword);
		} finally {
			permits.release();
		}
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		acquire();
		try {
			return delegate.matches(rawPassword, encodedPassword);
		} finally {
			permits.release();
		}
	}

	@Override
	public boolean upgradeEncoding(String encodedPassword) {
		return delegate.upgradeEncoding(encodedPassword);
	}

	private void acquire() {
		try {
			if (!permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw busy();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw busy();
		}
	}

	private static ResponseStatusException busy() {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Server busy, retry shortly");
	}
}
