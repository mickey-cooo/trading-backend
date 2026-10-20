package trading.demo.service;

import java.util.UUID;

import org.springframework.data.domain.Page;

import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;

public interface OrderService {
	// A market order fills at the current quote. A limit order reserves its funds
	// and waits as OPEN, unless the
	// quote has already reached its price. All in one transaction: if any part
	// fails, nothing is saved.
	// Same user, key and request gives the first order back; same key with a
	// different request is a ConflictException.
	OrderResponse submit(UUID userId, String idempotencyKey, SubmitOrderRequest request);

	// Cancels an open order and gives its reserved funds back, once. Cancelling
	// again returns the same order;
	// a filled order is a ConflictException.
	OrderResponse cancel(UUID userId, UUID orderId);

	// Fills every open limit order whose price has been reached, oldest first.
	void matchOpenOrders();

	// the caller's orders, newest first; another user's order is a
	// ResourceNotFoundException
	Page<OrderResponse> list(UUID userId, int page, int size);

	OrderResponse get(UUID userId, UUID orderId);

	// fills of the caller's orders, newest first
	Page<TradeResponse> trades(UUID userId, int page, int size);
}
