package trading.demo.service;

import java.util.UUID;

import org.springframework.data.domain.Page;

import trading.demo.model.dto.orderDto.OrderResponse;
import trading.demo.model.dto.orderDto.SubmitOrderRequest;
import trading.demo.model.dto.orderDto.TradeResponse;

public interface OrderService {
	// Fills a market order at the current quote, all in one transaction: if any
	// part fails, nothing is saved.
	// Same user, key and request gives the first order back; same key with a
	// different request is a ConflictException.
	OrderResponse submit(UUID userId, String idempotencyKey, SubmitOrderRequest request);

	// the caller's orders, newest first; another user's order is a
	// ResourceNotFoundException
	Page<OrderResponse> list(UUID userId, int page, int size);

	OrderResponse get(UUID userId, UUID orderId);

	// fills of the caller's orders, newest first
	Page<TradeResponse> trades(UUID userId, int page, int size);
}
