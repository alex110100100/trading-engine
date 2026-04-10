# Trading Engine

A small **Spring Boot** trading engine in Java: submit orders, match by **price–time priority**, and inspect trades and **per-symbol** order books. Prices and quantities use **`BigDecimal`** in the domain and JSON.

## Requirements

- **Java 17**
- **Maven**


## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/order` | Submit an order (JSON body). Returns `{ "orderId", "status" }` with status `ACCEPTED`, `PARTIALLY_FILLED`, or `FILLED`. |
| `GET` | `/trades` | All trades across symbols, merged and sorted by execution time (deterministic tie-break). |
| `GET` | `/orderbook?symbol=&limit=` | Snapshot of top bid/ask **levels for one symbol** (e.g. `BTC/USD`). `symbol` is required; `limit` defaults to `10`, minimum `1`. |
| `GET` | `/order/{id}` | Latest known status for an order id. Returns `{ "orderId", "status" }` or **404** if unknown. |
| `DELETE` | `/order/{id}` | Cancel a **resting** order by id (scans all symbol books). **204** if removed, **404** if not found. A canceled order will report `CANCELLED` from `GET /order/{id}`. |

### OpenAPI / Swagger UI

With the app running, interactive docs and “Try it out” are at:

**http://localhost:8080/swagger-ui.html**

Raw OpenAPI JSON: **http://localhost:8080/v3/api-docs**

### Example: submit order

```bash
curl -s -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{"id":"o1","symbol":"BTC/USD","orderSide":"BUY","price":50000,"quantity":0.1}'
```

### Validation and errors

Request bodies and query parameters are validated. Typical responses:

- **400** — invalid JSON, missing required fields, or constraint violations (e.g. blank `symbol`, `limit` below 1).

Error body shape (`application/json`):

```json
{
  "timestamp": "2026-04-08T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": {
    "symbol": "symbol is required",
    "quantity": "must be greater than 0"
  }
}
```

`fieldErrors` may be empty for non-field errors (e.g. malformed JSON).

## How matching works

1. **`MatchingEngine`** routes each order to an **`OrderBook`** for its **`symbol`** (books are created on demand).
2. **BUY** matches against the best **ask** prices; **SELL** against the best **bid** prices, until filled or nothing crosses.
3. Partial fills are supported; remainder rests in that symbol’s book.
4. Each execution produces an immutable **`Trade`**; **`GET /trades`** aggregates trades from all books.

### Price–time priority

- **Price**: Better prices first (higher bid, lower ask).
- **Time**: At the same price, earlier orders match first (**`LinkedHashMap`** insertion order per level).

#### Implementation (per symbol)

- **Bids**: `TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>>` — **descending** key order (best bid first).
- **Asks**: `TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>>` — **ascending** key order (best ask first).
- Each **`BookEntry`** tracks remaining quantity for partial fills.


## Next steps (ideas)

- Concurrency (single-writer / partitioned books).
- Persistence for trades and recovery.
