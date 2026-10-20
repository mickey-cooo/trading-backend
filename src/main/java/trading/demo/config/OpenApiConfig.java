package trading.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// API docs at /v3/api-docs and /swagger-ui.html. Routes that need a login take the access token as a Bearer token.
@Configuration
public class OpenApiConfig {
	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI tradingOpenApi() {
		return new OpenAPI()
				.info(new Info().title("Paper Trading API").version("v1")
						.description("Trade with demo money. Orders need an Idempotency-Key header; "
								+ "amounts and prices are decimal strings."))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
