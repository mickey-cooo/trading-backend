package trading.demo.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import trading.demo.model.dto.accountDto.BalanceResponse;
import trading.demo.model.dto.accountDto.LedgerEntryResponse;
import trading.demo.model.entity.AccountEntity;
import trading.demo.model.entity.AssetEntity;
import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.LedgerType;

public interface LedgerService {
	// one line of a movement; amount is signed
	record Leg(AccountEntity account, AssetEntity asset, BalanceBucket bucket, BigDecimal amount) {
	}

	// the user's account; the first call creates it and grants the starting USDT
	AccountEntity findOrOpen(UUID userId);

	// the other side of every movement; created on first use
	AccountEntity systemAccount();

	// Posts legs in one transaction (sum to zero per asset). False if already posted; throws InsufficientFundsException below zero.
	boolean post(String reference, LedgerType type, List<Leg> legs);

	List<BalanceResponse> balances(UUID userId);

	// newest first
	Page<LedgerEntryResponse> ledger(UUID userId, int page, int size);
}
