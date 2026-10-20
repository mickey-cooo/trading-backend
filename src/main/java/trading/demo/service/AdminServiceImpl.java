package trading.demo.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import trading.demo.exception.ConflictException;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.exception.ValidationException;
import trading.demo.mapper.UserMapper;
import trading.demo.model.dto.marketDto.MarketResponse;
import trading.demo.model.dto.marketDto.QuoteResponse;
import trading.demo.model.dto.userDto.UserResponseDTO;
import trading.demo.model.entity.AuditLogEntity;
import trading.demo.model.enums.AuditAction;
import trading.demo.model.enums.Common;
import trading.demo.repository.AuditLogRepository;
import trading.demo.repository.UserRepository;

@Service
public class AdminServiceImpl implements AdminService {
	private final MarketService marketService;
	private final OrderService orderService;
	private final UserRepository userRepository;
	private final AuditLogRepository auditLogRepository;
	private final UserMapper userMapper;

	public AdminServiceImpl(MarketService marketService, OrderService orderService, UserRepository userRepository,
			AuditLogRepository auditLogRepository, UserMapper userMapper) {
		this.marketService = marketService;
		this.orderService = orderService;
		this.userRepository = userRepository;
		this.auditLogRepository = auditLogRepository;
		this.userMapper = userMapper;
	}

	@Override
	@Transactional
	public QuoteResponse publishQuote(UUID adminId, String pair, BigDecimal price) {
		var quote = marketService.publishQuote(pair, price);
		writeAuditLog(adminId, AuditAction.QUOTE_PUBLISHED, quote.pair(), quote.price());
		return quote;
	}

	@Override
	@Transactional
	public MarketResponse changeMarketStatus(UUID adminId, String pair, Common status) {
		var market = marketService.changeStatus(pair, status);
		writeAuditLog(adminId, AuditAction.MARKET_STATUS_CHANGED, market.pair(), status.name());
		return market;
	}

	@Override
	@Transactional
	public UserResponseDTO changeUserStatus(UUID adminId, UUID userId, Common status) {
		if (status != Common.ACTIVE && status != Common.SUSPENDED) {
			throw new ValidationException("A user can only be ACTIVE or SUSPENDED");
		}
		// an admin who suspends themselves could lock everyone out of the admin routes
		if (adminId.equals(userId)) {
			throw new ConflictException("You cannot change your own status");
		}
		var user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
		if (user.getStatus() == Common.DELETED) {
			throw new ConflictException("A deleted user cannot be changed");
		}
		if (status == Common.SUSPENDED) {
			orderService.cancelOpenOrders(userId);
		}
		user.setStatus(status);
		writeAuditLog(adminId, AuditAction.USER_STATUS_CHANGED, userId.toString(), status.name());
		return userMapper.toDTO(user);
	}

	private void writeAuditLog(UUID adminId, AuditAction action, String target, String detail) {
		var entry = new AuditLogEntity();
		entry.setAdminId(adminId);
		entry.setAction(action);
		entry.setTarget(target);
		entry.setDetail(detail);
		auditLogRepository.save(entry);
	}
}
