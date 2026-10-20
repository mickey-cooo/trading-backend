package trading.demo.service;

import java.util.UUID;

import org.springframework.data.domain.Page;

import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;
import trading.demo.model.enums.OrderStatus;

public interface OrderService {
	// Market fills now; limit reserves and waits. Same key and request returns the first order; a different request is a ConflictException.
	OrderResponse submit(UUID userId, String idempotencyKey, SubmitOrderRequest request);

	// Cancels an open order and releases its reserve once. Cancelling twice is fine; a filled order is a ConflictException.
	OrderResponse cancel(UUID userId, UUID orderId);

	// Fills every open limit order whose price has been reached, oldest first.
	void matchOpenOrders();

	// Cancels all of the user's open orders and releases their reserves; joins the caller's transaction. Returns how many.
	int cancelOpenOrders(UUID userId);

	// the caller's orders, newest first; status null means all of them
	Page<OrderResponse> list(UUID userId, OrderStatus status, int page, int size);

	OrderResponse get(UUID userId, UUID orderId);

	// fills of the caller's orders, newest first
	Page<TradeResponse> trades(UUID userId, int page, int size);
}
