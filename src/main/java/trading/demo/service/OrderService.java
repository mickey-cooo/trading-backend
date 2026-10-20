package trading.demo.service;

import java.util.UUID;

import org.springframework.data.domain.Page;

import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;
import trading.demo.model.enums.OrderStatus;

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

	// Cancels every open order of the user and gives the reserves back. Joins the
	// caller's transaction, so an admin can suspend the user in the same one.
	// Returns how many were cancelled.
	int cancelOpenOrders(UUID userId);

	// the caller's orders, newest first; status null means all of them
	Page<OrderResponse> list(UUID userId, OrderStatus status, int page, int size);

	OrderResponse get(UUID userId, UUID orderId);

	// fills of the caller's orders, newest first
	Page<TradeResponse> trades(UUID userId, int page, int size);
}
