package trading.demo.config;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import trading.demo.exception.ResourceNotFoundException;
import trading.demo.model.entity.AssetEntity;
import trading.demo.model.entity.MarketEntity;
import trading.demo.repository.AssetRepository;
import trading.demo.repository.MarketRepository;
import trading.demo.service.MarketService;

// On startup, creates missing assets and pairs. Safe to rerun.
@Component
public class MarketSeeder implements ApplicationRunner {
	// stock tickers traded against USDT, same as the crypto pairs
	private static final Map<String, String> STOCKS = Map.ofEntries(Map.entry("AAPL", "Apple"),
			Map.entry("MSFT", "Microsoft"), Map.entry("GOOGL", "Alphabet"), Map.entry("AMZN", "Amazon"),
			Map.entry("NVDA", "Nvidia"), Map.entry("TSLA", "Tesla"), Map.entry("META", "Meta Platforms"),
			Map.entry("NFLX", "Netflix"), Map.entry("AMD", "Advanced Micro Devices"), Map.entry("INTC", "Intel"));

	private final AssetRepository assetRepository;
	private final MarketRepository marketRepository;
	private final MarketProperties marketProperties;
	private final MarketService marketService;
	private final SimulatorProperties simulatorProperties;

	public MarketSeeder(AssetRepository assetRepository, MarketRepository marketRepository,
			MarketProperties marketProperties, MarketService marketService, SimulatorProperties simulatorProperties) {
		this.assetRepository = assetRepository;
		this.marketRepository = marketRepository;
		this.marketProperties = marketProperties;
		this.marketService = marketService;
		this.simulatorProperties = simulatorProperties;
	}

	@Override
	public void run(ApplicationArguments args) {
		var bitcoin = createAssetIfMissing("BTC", "Bitcoin");
		var ether = createAssetIfMissing("ETH", "Ether");
		var tether = createAssetIfMissing("USDT", "Tether");

		createMarketIfMissing(bitcoin, tether);
		createMarketIfMissing(ether, tether);

		STOCKS.forEach((symbol, name) -> {
			createMarketIfMissing(createAssetIfMissing(symbol, name), tether);
			publishFirstQuoteIfMissing(symbol + "-" + tether.getSymbol());
		});
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
		var pair = baseAsset.getSymbol() + "-" + quoteAsset.getSymbol();
		if (marketRepository.existsByPair(pair)) {
			return;
		}
		var market = new MarketEntity();
		market.setPair(pair);
		market.setBaseAsset(baseAsset);
		market.setQuoteAsset(quoteAsset);
		market.setMinQuantityStep(BigDecimal.ONE.movePointLeft(marketProperties.quantityDecimals()));
		market.setMinPriceStep(BigDecimal.ONE.movePointLeft(marketProperties.priceDecimals()));
		marketRepository.save(market);
	}

	// A stock shows a price right away, even with the simulator off. Uses the simulator start price; a stock with a quote is left alone.
	private void publishFirstQuoteIfMissing(String pair) {
		var startPrice = simulatorProperties.startPrices().get(pair);
		if (startPrice == null) {
			return;
		}
		try {
			marketService.getCurrentQuote(pair);
		} catch (ResourceNotFoundException noQuoteYet) {
			marketService.publishQuote(pair, startPrice);
		}
	}
}
