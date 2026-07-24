# No payment handling — off-platform settlement

The platform handles no money. At Close it creates a Deal, reveals the two parties' contacts, and opens a Chat Thread; buyer and seller arrange payment and exchange themselves (the common Philippine classifieds pattern). We deliberately omit a payment service to avoid gateway integration, PCI scope, and business-registration blockers that aren't the point of this project. The distributed-transaction learning goal is met instead at Close: the winner is selected exactly once, idempotently, even if `auction.closed` is redelivered.
