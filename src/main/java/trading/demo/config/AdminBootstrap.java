package trading.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import trading.demo.model.entity.UserEntity;
import trading.demo.model.enums.RoleType;
import trading.demo.repository.UserRepository;

// Creates the first ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD. Never promotes an existing user.
@Component
public class AdminBootstrap implements ApplicationRunner {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;

	public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isBlank()) {
			return;
		}
		if (password.length() < 8 || password.length() > 72) {
			throw new IllegalStateException("app.admin.password must be 8 to 72 characters");
		}
		if (userRepository.findByEmail(email).isPresent()) {
			return;
		}
		var admin = new UserEntity();
		admin.setEmail(email);
		admin.setPassword(passwordEncoder.encode(password));
		admin.setRole(RoleType.ADMIN);
		userRepository.save(admin);
	}
}
