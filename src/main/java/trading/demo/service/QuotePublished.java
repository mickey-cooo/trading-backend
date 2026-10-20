package trading.demo.service;

// Sent inside the publishQuote transaction; listeners run only after commit.
public record QuotePublished(String pair, String price) {
}
