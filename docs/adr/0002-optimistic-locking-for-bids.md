# Optimistic locking for concurrent bids

Concurrent Bids on a hot Auction are resolved with optimistic locking (JPA `@Version` + retry), not pessimistic row locks. Pessimistic `SELECT ... FOR UPDATE` would serialize every Bid on a popular Auction and collapse throughput; a Redis atomic counter would split the source of truth. Optimistic locking keeps Postgres authoritative, holds no locks, and a stale Bid (version moved) is rejected — which is also our balking behaviour. Redis is used only as a read cache for the current price.
