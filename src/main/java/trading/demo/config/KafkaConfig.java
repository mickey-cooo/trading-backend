package trading.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Kafka is off unless KAFKA_ENABLED=true, so tests and CI need no broker.
@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {
	public static final String QUOTES_TOPIC = "market.quotes";

	// shortcut: one replica fits one local broker, raise it for a cluster
	@Bean
	NewTopic quotesTopic() {
		return TopicBuilder.name(QUOTES_TOPIC).partitions(3).replicas(1).build();
	}
}
