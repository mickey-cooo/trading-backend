package trading.demo.model.dto.marketDto;

// One chart candle. time is the slot start in Unix seconds; prices are plain strings, never floats.
public record CandleResponse(long time, String open, String high, String low, String close) {
}
