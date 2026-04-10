# Trading Engine

A small **Spring Boot** trading engine in Java: submit orders, match by **price–time priority**, and inspect trades and **per-symbol** order books. Prices and quantities use **`BigDecimal`** in the domain and JSON.

## Requirements

- **Java 17**
- **Maven** (or `./mvnw`)
- **PostgreSQL** reachable at runtime (see [Configuration](#configuration)) — e.g. local install or **Docker** on another machine on your LAN.



## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/order` | Submit an order (JSON body). Returns `{ "orderId", "status" }` with status `ACCEPTED`, `PARTIALLY_FILLED`, or `FILLED`. |
| `GET` | `/trades` | Trade history sorted by execution time (deterministic tie-break). **Reads from PostgreSQL** (`trades` table) when the app runs with JPA, so history survives restarts. |
| `GET` | `/orderbook?symbol=&limit=` | Snapshot of top bid/ask **levels for one symbol** (e.g. `BTC/USD`). `symbol` is required; `limit` defaults to `10`, minimum `1`. |
| `GET` | `/order/{id}` | Latest known **in-memory** status for an order id. Returns `{ "orderId", "status" }` or **404** if unknown. (`CANCELLED` after a successful cancel.) |
| `DELETE` | `/order/{id}` | Cancel a **resting** order. **204** if removed, **404** if not found. |

### OpenAPI / Swagger UI

With the app running:

- **http://localhost:8080/swagger-ui.html**
- **http://localhost:8080/v3/api-docs** (OpenAPI JSON)

### Example: submit order

```bash
curl -s -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{"id":"o1","symbol":"BTC/USD","orderSide":"BUY","price":50000,"quantity":0.1}'
```

### Validation and errors

Request bodies and query parameters are validated. Typical **400** responses for invalid JSON, missing fields, or constraint violations.

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

## Persistence (current scope)

- **Trades:** On each match, new trades are written to the **`trades`** table. **`GET /trades`** loads from that table in the running application.
- **Order book & order status:** Still **in-memory** after restart unless you reload them (next major step: persist open orders and replay into books).

## How matching works

1. **`MatchingEngine`** routes each order to an **`OrderBook`** for its **symbol** (books are created on demand).
2. **BUY** matches against the best **ask** prices; **SELL** against the best **bid** prices, until filled or nothing crosses.
3. Partial fills are supported; remainder rests in that symbol’s book.
4. Each execution produces an immutable **`Trade`**; trades are appended in the book and **persisted** when JPA is enabled.

### Concurrency

- **Per-symbol locks:** different symbols can be processed on different threads in parallel; the same symbol is serialized so matching stays deterministic.

### Price–time priority

- **Price:** Better prices first (higher bid, lower ask).
- **Time:** At the same price, earlier orders match first (**`LinkedHashMap`** insertion order per level).

#### Implementation (per symbol)

- **Bids:** `TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>>` — **descending** key order (best bid first).
- **Asks:** `TreeMap<BigDecimal, LinkedHashMap<String, BookEntry>>` — **ascending** key order (best ask first).
- Each **`BookEntry`** tracks remaining quantity for partial fills.


## Next steps (ideas)

- Persist **open / resting orders** and **replay** the order book on startup.
- Persist **order status** (or events) so `GET /order/{id}` survives restarts.
- **Flyway** for schema migrations; **metrics / health / CI** for operability.
