# Bidding lives inside auctionservice, not a separate service

Bids and auction state share one consistency boundary — accepting a Bid must atomically check it against the current highest Bid. We keep bidding *inside* `auctionservice` rather than splitting a `biddingservice`, because a split would force a distributed lock across services for zero benefit. A separate bidding service is the intuitive-but-wrong microservice cut here; co-locating them is deliberate.
