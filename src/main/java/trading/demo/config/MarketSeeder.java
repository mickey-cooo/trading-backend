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
	private final AssetRepository assetRepository;
	private final MarketRepository marketRepository;
	private final MarketProperties marketProperties;

	public MarketSeeder(AssetRepository assetRepository, MarketRepository marketRepository,
			MarketProperties marketProperties) {
		this.assetRepository = assetRepository;
		this.marketRepository = marketRepository;
		this.marketProperties = marketProperties;
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
		market.setMinQuantityStep(BigDecimal.ONE.movePointLeft(marketProperties.quantityDecimals()));
		market.setMinPriceStep(BigDecimal.ONE.movePointLeft(marketProperties.priceDecimals()));
		marketRepository.save(market);
	}
}
