package trading.demo.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class JacksonLimitsTest {
	@Test
	void applicationPropertiesCapJsonStringLength() throws Exception {
		var props = new java.util.Properties();
		try (var in = getClass().getResourceAsStream("/application.properties")) {
			props.load(in);
		}
		var runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
				.withPropertyValues("spring.jackson.factory.constraints.read.max-string-length="
						+ props.getProperty("spring.jackson.factory.constraints.read.max-string-length"));
		runner.run(ctx -> {
			var mapper = ctx.getBean(JsonMapper.class);
			assertEquals("ok", mapper.readValue("\"ok\"", String.class));
			assertThrows(Exception.class, () -> mapper.readValue("\"" + "a".repeat(2000) + "\"", String.class));
		});
	}
}
