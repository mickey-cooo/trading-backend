package trading.demo.mapper;

import org.springframework.stereotype.Component;

import trading.demo.model.dto.accountDto.BalanceResponse;
import trading.demo.model.dto.accountDto.LedgerEntryResponse;
import trading.demo.model.entity.BalanceEntity;
import trading.demo.model.entity.LedgerEntryEntity;

@Component
public class AccountMapper {
	public BalanceResponse toBalanceResponse(BalanceEntity balance) {
		return new BalanceResponse(balance.getAsset().getSymbol(), balance.getAvailable().toPlainString(),
				balance.getReserved().toPlainString());
	}

	public LedgerEntryResponse toLedgerEntryResponse(LedgerEntryEntity entry) {
		return new LedgerEntryResponse(entry.getAsset().getSymbol(), entry.getBucket(),
				entry.getAmount().toPlainString(), entry.getType(), entry.getReference(), entry.getCreatedAt());
	}
}
