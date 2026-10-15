package trading.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TraddingApplication {

	public static void main(String[] args) {
		SpringApplication.run(TraddingApplication.class, args);
	}

}
