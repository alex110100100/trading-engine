# Trading Engine

A simple trading engine implementation in Java, designed to match BUY and SELL orders based on price-time priority.

## Features

- **Order Types**: Supports `BUY` and `SELL` orders.
- **Order Book**: Uses `TreeMap` and `LinkedHashMap` to maintain bids and asks with price-time priority.
- **Quantity-aware matching**: Partial fills supported; an order can match across multiple price levels or leave a remainder resting in the book.
- **Trades**: Every match (or partial fill) produces an immutable `Trade` record; `GET /trades` returns the list.
- **Cancellation**: Resting orders can be cancelled via `DELETE /order/{id}`; returns 204 if cancelled, 404 if not found (e.g. already matched or unknown id).

## How It Works

1. Orders are submitted via a REST API (`POST /order`).
2. The `MatchingEngine` processes orders:
   - **BUY** orders match against the best available **ASK**, then the next, until the order is filled or no ask crosses.
   - **SELL** orders match against the best available **BID** the same way.
3. Any unfilled quantity is added to the order book. Each match is recorded as a `Trade`.
4. `GET /trades` returns all trades in order.
5. `DELETE /order/{id}` cancels a resting order (removes it from the book). Returns 204 No Content if cancelled, 404 if the order is not in the book.

### Price-Time Priority

The trading engine ensures **price-time priority** for order matching:

- **Price Priority**: Orders are matched based on their price. Better prices (higher for bids, lower for asks) take precedence.
- **Time Priority**: If two orders have the same price, the one that arrived first is matched first.

#### Implementation Details

- **Bids**: Stored in a `TreeMap<Double, LinkedHashMap<String, BookEntry>>` sorted in **descending order** (highest bid first).
- **Asks**: Stored in a `TreeMap<Double, LinkedHashMap<String, BookEntry>>` sorted in **ascending order** (lowest ask first).
- At each price level, orders are stored as `BookEntry` (order + remaining quantity) in a `LinkedHashMap` for **insertion order** (time priority) and partial-fill tracking.

Example:

- **Bids**:
  - `31000.0`: [Order1, Order2] (Order1 arrived first)
  - `30900.0`: [Order3]
- **Asks**:
  - `31100.0`: [Order4, Order5] (Order4 arrived first)
  - `31200.0`: [Order6]

When a new order arrives:

- A **buy order** at `31100.0` matches with `Order4` (best ask price, first in time).
- A **sell order** at `31000.0` matches with `Order1` (best bid price, first in time).

## Next Steps

- Structured API responses (e.g. order status: ACCEPTED / PARTIALLY_FILLED / FILLED).
- Order book endpoint (e.g. `GET /orderbook`).

