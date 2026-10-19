package trading.demo.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import trading.demo.model.entity.AssetEntity;
import trading.demo.model.entity.InstrumentEntity;
import trading.demo.repository.AssetRepository;
import trading.demo.repository.InstrumentRepository;

// On startup, creates missing assets and pairs. Safe to rerun.
@Component
public class MarketSeeder implements ApplicationRunner {
	private static final BigDecimal QUANTITY_INCREMENT = new BigDecimal("0.00000001"); // 8 decimals
	private static final BigDecimal PRICE_INCREMENT = new BigDecimal("0.01"); // 2 decimals

	private final AssetRepository assetRepository;
	private final InstrumentRepository instrumentRepository;

	public MarketSeeder(AssetRepository assetRepository, InstrumentRepository instrumentRepository) {
		this.assetRepository = assetRepository;
		this.instrumentRepository = instrumentRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		var bitcoin = createAssetIfMissing("BTC", "Bitcoin");
		var ether = createAssetIfMissing("ETH", "Ether");
		var tether = createAssetIfMissing("USDT", "Tether");

		createInstrumentIfMissing(bitcoin, tether);
		createInstrumentIfMissing(ether, tether);
	}

	private AssetEntity createAssetIfMissing(String symbol, String name) {
		return assetRepository.findBySymbol(symbol).orElseGet(() -> {
			var asset = new AssetEntity();
			asset.setSymbol(symbol);
			asset.setName(name);
			return assetRepository.save(asset);
		});
	}

	private void createInstrumentIfMissing(AssetEntity baseAsset, AssetEntity quoteAsset) {
		var symbol = baseAsset.getSymbol() + "-" + quoteAsset.getSymbol();
		if (instrumentRepository.existsBySymbol(symbol)) {
			return;
		}
		var instrument = new InstrumentEntity();
		instrument.setSymbol(symbol);
		instrument.setBaseAsset(baseAsset);
		instrument.setQuoteAsset(quoteAsset);
		instrument.setQuantityIncrement(QUANTITY_INCREMENT);
		instrument.setPriceIncrement(PRICE_INCREMENT);
		instrumentRepository.save(instrument);
	}
}
