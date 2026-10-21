package trading.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Puts /api/v1 in front of every controller route, so controllers only write their own path. Security rules and clients still use the full path.
@Configuration
public class WebConfig implements WebMvcConfigurer {
	public static final String API_PREFIX = "/api/v1";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		// only our controllers, so Swagger and the error page keep their own paths
		configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage("trading.demo.controller"));
	}
}
