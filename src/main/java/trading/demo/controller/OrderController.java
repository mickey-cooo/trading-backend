package trading.demo.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;
import trading.demo.model.entity.UserEntity;
import trading.demo.service.OrderService;

// The caller's own orders and fills: the user always comes from the token, never the URL.
@RestController
@Validated
@RequestMapping("/api/v1")
public class OrderController {
	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	// a new Idempotency-Key per order; resend the same key to retry safely
	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse submit(@AuthenticationPrincipal UserEntity me,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
			@Valid @RequestBody SubmitOrderRequest request) {
		return orderService.submit(me.getId(), idempotencyKey, request);
	}

	// newest first; size 1-100, default 20
	@GetMapping("/orders")
	public PagedModel<OrderResponse> list(@AuthenticationPrincipal UserEntity me,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(orderService.list(me.getId(), page, size));
	}

	@GetMapping("/orders/{id}")
	public OrderResponse get(@AuthenticationPrincipal UserEntity me, @PathVariable UUID id) {
		return orderService.get(me.getId(), id);
	}

	@PostMapping("/orders/{id}/cancel")
	public OrderResponse cancel(@AuthenticationPrincipal UserEntity me, @PathVariable UUID id) {
		return orderService.cancel(me.getId(), id);
	}

	@GetMapping("/trades")
	public PagedModel<TradeResponse> trades(@AuthenticationPrincipal UserEntity me,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return new PagedModel<>(orderService.trades(me.getId(), page, size));
	}
}
