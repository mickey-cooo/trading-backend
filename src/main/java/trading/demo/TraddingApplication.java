package trading.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import trading.demo.config.SwaggerConfig;

@SpringBootApplication
public class TraddingApplication extends SwaggerConfig {

	public static void main(String[] args) {
		SpringApplication.run(TraddingApplication.class, args);
	}

}
