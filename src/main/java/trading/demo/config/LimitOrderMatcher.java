package trading.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import trading.demo.service.OrderService;

// Every second, fills open limit orders whose price was reached. Turn off with app.matcher.enabled=false (tests do).
@Component
@ConditionalOnProperty(prefix = "app.matcher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LimitOrderMatcher {
	private final OrderService orderService;

	public LimitOrderMatcher(OrderService orderService) {
		this.orderService = orderService;
	}

	@Scheduled(fixedDelay = 1000)
	public void tick() {
		orderService.matchOpenOrders();
	}
}
