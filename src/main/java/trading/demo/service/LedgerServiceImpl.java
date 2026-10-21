package trading.demo.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import trading.demo.exception.InsufficientFundsException;
import trading.demo.mapper.AccountMapper;
import trading.demo.model.dto.accountDto.BalanceResponse;
import trading.demo.model.dto.accountDto.LedgerEntryResponse;
import trading.demo.model.entity.AccountEntity;
import trading.demo.model.entity.BalanceEntity;
import trading.demo.model.entity.LedgerEntryEntity;
import trading.demo.model.enums.BalanceBucket;
import trading.demo.model.enums.LedgerType;
import trading.demo.repository.AccountRepository;
import trading.demo.repository.AssetRepository;
import trading.demo.repository.BalanceRepository;
import trading.demo.repository.LedgerEntryRepository;
import trading.demo.repository.UserRepository;

@Service
public class LedgerServiceImpl implements LedgerService {
	static final String FUNDING_ASSET = "USDT";
	static final BigDecimal FUNDING_AMOUNT = new BigDecimal("10000");
	// matches NUMERIC(38,18); more decimals are rejected, never rounded
	private static final int SCALE = 18;

	private final AccountRepository accountRepository;
	private final BalanceRepository balanceRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final AssetRepository assetRepository;
	private final UserRepository userRepository;
	private final AccountMapper accountMapper;
	private final EntityManager entityManager;
	// own transaction, so a lost creation race rolls back alone
	private final TransactionTemplate newTransaction;

	public LedgerServiceImpl(AccountRepository accountRepository, BalanceRepository balanceRepository,
			LedgerEntryRepository ledgerEntryRepository, AssetRepository assetRepository, UserRepository userRepository,
			AccountMapper accountMapper, EntityManager entityManager, PlatformTransactionManager transactionManager) {
		this.accountRepository = accountRepository;
		this.balanceRepository = balanceRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.assetRepository = assetRepository;
		this.userRepository = userRepository;
		this.accountMapper = accountMapper;
		this.entityManager = entityManager;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	public AccountEntity findOrOpen(UUID userId) {
		return accountRepository.findByUserId(userId).orElseGet(() -> open(userId));
	}

	private AccountEntity open(UUID userId) {
		var system = systemAccount();
		try {
			return newTransaction.execute(status -> {
				var account = new AccountEntity();
				account.setUser(userRepository.getReferenceById(userId));
				// flush now: a second creator for this user fails here, before any money moves
				accountRepository.saveAndFlush(account);
				// shortcut: assets added later get no row, post() fails for them; add a backfill then
				var assets = assetRepository.findAll();
				assets.forEach(asset -> {
					var balance = new BalanceEntity();
					balance.setAccount(account);
					balance.setAsset(asset);
					balanceRepository.save(balance);
				});
				var usdt = assets.stream().filter(asset -> asset.getSymbol().equals(FUNDING_ASSET)).findFirst()
						.orElseThrow(() -> new IllegalStateException("Asset " + FUNDING_ASSET + " is not seeded"));
				post("funding:" + account.getId(), LedgerType.FUNDING,
						List.of(new Leg(system, usdt, BalanceBucket.AVAILABLE, FUNDING_AMOUNT.negate()),
								new Leg(account, usdt, BalanceBucket.AVAILABLE, FUNDING_AMOUNT)));
				return account;
			});
		} catch (DataIntegrityViolationException raced) {
			return accountRepository.findByUserId(userId).orElseThrow(() -> raced);
		}
	}

	@Override
	public AccountEntity systemAccount() {
		return accountRepository.findBySystemSlot(true).orElseGet(() -> {
			var system = new AccountEntity();
			system.setSystemSlot(true);
			try {
				return newTransaction.execute(status -> accountRepository.saveAndFlush(system));
			} catch (DataIntegrityViolationException raced) {
				return accountRepository.findBySystemSlot(true).orElseThrow(() -> raced);
			}
		});
	}

	@Override
	@Transactional
	public boolean post(String reference, LedgerType type, List<Leg> legs) {
		requireBalanced(legs);
		var userAccountIds = legs.stream().map(Leg::account).filter(account -> !account.isSystem())
				.map(AccountEntity::getId).distinct().toList();
		// lock first: a concurrent post of the same reference waits here, then sees its ledger lines below
		var rows = userAccountIds.isEmpty()
				? List.<BalanceEntity>of()
				: balanceRepository.lockByAccountIds(userAccountIds);
		// a caller may have loaded these rows earlier in this transaction; read the committed values
		rows.forEach(row -> entityManager.refresh(row));
		if (ledgerEntryRepository.existsByReference(reference)) {
			return false;
		}
		for (var leg : legs) {
			var amount = leg.amount().setScale(SCALE);
			if (!leg.account().isSystem()) {
				apply(rows, leg, amount);
			}
			var line = new LedgerEntryEntity();
			line.setAccount(leg.account());
			line.setAsset(leg.asset());
			line.setBucket(leg.bucket());
			line.setAmount(amount);
			line.setType(type);
			line.setReference(reference);
			ledgerEntryRepository.save(line);
		}
		return true;
	}

	private static void apply(List<BalanceEntity> rows, Leg leg, BigDecimal amount) {
		var row = rows.stream()
				.filter(candidate -> candidate.getAccount().getId().equals(leg.account().getId())
						&& candidate.getAsset().getId().equals(leg.asset().getId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No balance row for " + leg.asset().getSymbol()));
		var changed = (leg.bucket() == BalanceBucket.AVAILABLE ? row.getAvailable() : row.getReserved()).add(amount);
		if (changed.signum() < 0) {
			throw new InsufficientFundsException();
		}
		if (leg.bucket() == BalanceBucket.AVAILABLE) {
			row.setAvailable(changed);
		} else {
			row.setReserved(changed);
		}
	}

	// every movement must add up to zero per asset, or money appears from nowhere
	private static void requireBalanced(List<Leg> legs) {
		var sums = new HashMap<UUID, BigDecimal>();
		legs.forEach(leg -> {
			if (leg.amount().stripTrailingZeros().scale() > SCALE) {
				throw new IllegalArgumentException("Amount has more than " + SCALE + " decimals");
			}
		});
		legs.forEach(leg -> sums.merge(leg.asset().getId(), leg.amount(), BigDecimal::add));
		if (legs.isEmpty() || sums.values().stream().anyMatch(sum -> sum.signum() != 0)) {
			throw new IllegalArgumentException("Legs must add up to zero per asset");
		}
	}

	// not @Transactional: findOrOpen opens its own transactions, and an outer one would hold a second connection
	@Override
	public List<BalanceResponse> balances(UUID userId) {
		var account = findOrOpen(userId);
		return balanceRepository.findByAccountIdOrderByAssetSymbolAsc(account.getId()).stream()
				.map(accountMapper::toBalanceResponse).toList();
	}

	@Override
	public Page<LedgerEntryResponse> ledger(UUID userId, int page, int size) {
		var account = findOrOpen(userId);
		var order = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
		return ledgerEntryRepository.findByAccountId(account.getId(), PageRequest.of(page, size, order))
				.map(accountMapper::toLedgerEntryResponse);
	}
}
