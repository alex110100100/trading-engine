# Trading Engine

A simple trading engine implementation in Java, designed to match BUY and SELL orders based on price-time priority.

## Features
- **Order Types**: Supports `BUY` and `SELL` orders.
- **Order Book**: Uses `TreeMap` and `LinkedHashMap` to maintain bids and asks with price-time priority.
- **Matching Logic**: Matches orders based on price-time priority (simplified for now).

## How It Works
1. Orders are submitted via a REST API (`/order` endpoint).
2. The `MatchingEngine` processes orders:
    - `BUY` orders are matched against the best available `ASK`.
    - `SELL` orders are matched against the best available `BID`.
3. Unmatched orders are added to the order book.

### Price-Time Priority
The trading engine ensures **price-time priority** for order matching:
- **Price Priority**: Orders are matched based on their price. Better prices (higher for bids, lower for asks) take precedence.
- **Time Priority**: If two orders have the same price, the one that arrived first is matched first.

#### Implementation Details
- **Bids**: Stored in a `TreeMap<Double, LinkedHashMap<String, Order>>` sorted in **descending order** (highest bid first).
- **Asks**: Stored in a `TreeMap<Double, LinkedHashMap<String, Order>>` sorted in **ascending order** (lowest ask first).
- At each price level, orders are stored in a `LinkedHashMap` to maintain **insertion order** (time priority).

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
- Take quantity into account during order matching.
- Add support for order cancellation.
- Create "trades" when orders get matched