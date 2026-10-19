package trading.demo.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import trading.demo.model.entity.AssetEntity;
import trading.demo.model.entity.MarketEntity;
import trading.demo.repository.AssetRepository;
import trading.demo.repository.MarketRepository;

// On startup, creates missing assets and pairs. Safe to rerun.
@Component
public class MarketSeeder implements ApplicationRunner {
	private static final BigDecimal MIN_QUANTITY_STEP = new BigDecimal("0.00000001"); // 8 decimals
	private static final BigDecimal MIN_PRICE_STEP = new BigDecimal("0.01"); // 2 decimals

	private final AssetRepository assetRepository;
	private final MarketRepository marketRepository;

	public MarketSeeder(AssetRepository assetRepository, MarketRepository marketRepository) {
		this.assetRepository = assetRepository;
		this.marketRepository = marketRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		var bitcoin = createAssetIfMissing("BTC", "Bitcoin");
		var ether = createAssetIfMissing("ETH", "Ether");
		var tether = createAssetIfMissing("USDT", "Tether");

		createMarketIfMissing(bitcoin, tether);
		createMarketIfMissing(ether, tether);
	}

	private AssetEntity createAssetIfMissing(String symbol, String name) {
		return assetRepository.findBySymbol(symbol).orElseGet(() -> {
			var asset = new AssetEntity();
			asset.setSymbol(symbol);
			asset.setName(name);
			return assetRepository.save(asset);
		});
	}

	private void createMarketIfMissing(AssetEntity baseAsset, AssetEntity quoteAsset) {
		var symbol = baseAsset.getSymbol() + "-" + quoteAsset.getSymbol();
		if (marketRepository.existsBySymbol(symbol)) {
			return;
		}
		var market = new MarketEntity();
		market.setSymbol(symbol);
		market.setBaseAsset(baseAsset);
		market.setQuoteAsset(quoteAsset);
		market.setMinQuantityStep(MIN_QUANTITY_STEP);
		market.setMinPriceStep(MIN_PRICE_STEP);
		marketRepository.save(market);
	}
}
