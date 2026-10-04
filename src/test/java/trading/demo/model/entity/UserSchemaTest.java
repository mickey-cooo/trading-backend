package trading.demo.model.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Run with TEST_DATABASE_URL, TEST_DATABASE_USERNAME and
 * TEST_DATABASE_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class UserSchemaTest {
	@Test
	void createsAndPersistsUsersWithDatabaseConstraints() throws Exception {
		String url = System.getenv("TEST_DATABASE_URL");
		String username = System.getenv("TEST_DATABASE_USERNAME");
		String password = System.getenv("TEST_DATABASE_PASSWORD");
		String schema = "user_schema_test_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(url, username, password);
				var sql = connection.createStatement()) {
			try {
				sql.execute("CREATE SCHEMA " + schema);
				var configuration = new Configuration().addAnnotatedClass(UserEntity.class)
						.addAnnotatedClass(AddressEntity.class).setProperty("hibernate.connection.url", url)
						.setProperty("hibernate.connection.username", username)
						.setProperty("hibernate.connection.password", password)
						.setProperty("hibernate.default_schema", schema)
						.setProperty("hibernate.hbm2ddl.auto", "update");
				try (var factory = configuration.buildSessionFactory()) {
					UUID id;
					try (var session = factory.openSession()) {
						var transaction = session.beginTransaction();
						var user = new UserEntity();
						user.setEmail("  Alice@Example.COM  ");
						user.setFirstName("Alice");
						user.setLastName("Example");
						user.setPassword("test-only-encoded-value");
						session.persist(user);
						var address = new AddressEntity();
						address.setUser(user);
						address.setCountry("Thailand");
						session.persist(address);
						transaction.commit();
						id = user.getId();
						assertNotNull(id);
					}
					try (var session = factory.openSession()) {
						var user = session.find(UserEntity.class, id);
						assertEquals("alice@example.com", user.getEmail());
						assertNotNull(user.getCreatedAt());
						assertEquals(1, user.getAddresses().size());
						assertEquals(id, user.getAddresses().getFirst().getUser().getId());
					}

					sql.execute("SET search_path TO " + schema);
					String duplicateUser = "INSERT INTO users "
							+ "(id, email, password, first_name, last_name, status, role, user_type, created_at, updated_at) "
							+ "VALUES (gen_random_uuid(), 'alice@example.com', 'test-hash', 'A', 'B', "
							+ "'ACTIVE', 'USER', 'TRADER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
					assertEquals("23505",
							assertThrows(SQLException.class, () -> sql.execute(duplicateUser)).getSQLState());
					assertEquals("23503", assertThrows(SQLException.class,
							() -> sql.execute("INSERT INTO user_addresses (id, user_id, created_at, updated_at) "
									+ "VALUES (gen_random_uuid(), gen_random_uuid(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
							.getSQLState());
				}
			} finally {
				sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
			}
		}
	}
}
