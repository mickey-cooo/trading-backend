package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class BoundedPasswordEncoderTest {
	@Test
	void rejectsWithServiceUnavailableWhenAllPermitsAreBusy() throws Exception {
		var inside = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		PasswordEncoder blocking = new PasswordEncoder() {
			public String encode(CharSequence raw) {
				return raw.toString();
			}

			public boolean matches(CharSequence raw, String encoded) {
				inside.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return true;
			}
		};
		var bounded = new BoundedPasswordEncoder(blocking, 1);
		var holder = new Thread(() -> bounded.matches("a", "a"));
		holder.start();
		assertTrue(inside.await(5, TimeUnit.SECONDS));

		var e = assertThrows(ResponseStatusException.class, () -> bounded.encode("b"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
		release.countDown();
		holder.join();
		assertEquals("c", bounded.encode("c"));
	}
}
