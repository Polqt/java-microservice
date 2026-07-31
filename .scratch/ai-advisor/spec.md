# Spec: Provider-Neutral AI Advisor

Status: ready-for-agent

## Problem Statement

A rules-first Proxy Bidder can safely calculate the next valid Bid, but it cannot make a limited judgement call about whether placing that otherwise-valid Bid still matches the Bidder's stated preference. Tying this judgement directly to Gemini would create avoidable vendor lock-in, cost risk, and an operational dependency in the synchronous bidding path.

The AI Advisor must therefore remain optional and advisory. It may recommend whether to continue with a rules-approved Bid, but it must never calculate or alter the amount, exceed the Budget, bypass Auction rules, place a Bid, or prevent rules-only operation when an AI provider is unavailable.

## Solution

Add a stateless `aiservice` that accepts a small, service-authenticated advice request from `agentservice` after the rules engine has already calculated a valid proposed amount. The service returns a structured `BID` or `SKIP` recommendation with a bounded reason.

`agentservice` remains the orchestrator and authority. It decides whether a Proxy Bidder is AI-assisted, validates fresh Auction state, calculates the proposed amount, enforces the Budget, and places the Bid through the existing authenticated Auction command. If `aiservice`, its model provider, or its response fails, `agentservice` immediately falls back to the existing rules-first decision.

The model integration is provider-neutral. V1 uses Groq-hosted `llama-3.1-8b-instant` as the low-cost hosted default. Local development may instead use an Apache-2.0 Qwen3 instruct model through Ollama. Both adapters expose the same application interface and structured result. Gemini may be added later without changing the advice API or Proxy Bidder rules.

## User Stories

1. As a Bidder, I want AI assistance to be optional, so that I can choose rules-only behavior.
2. As a Bidder, I want my Proxy Bidder's Budget to remain absolute, so that AI can never authorize a higher amount.
3. As a Bidder, I want AI to evaluate only a rules-approved proposed Bid, so that invalid Bids never reach the model.
4. As a Bidder, I want AI to recommend `BID` or `SKIP`, so that its authority is narrow and understandable.
5. As a Bidder, I want a short explanation for an AI recommendation, so that autonomous behavior can be reviewed.
6. As a Bidder, I want AI failure to fall back to rules-first bidding, so that provider downtime does not disable my Proxy Bidder.
7. As a Bidder, I want a slow AI response ignored, so that a model cannot make my Proxy Bidder miss a time-sensitive Bid.
8. As a Bidder, I want malformed AI output ignored, so that an invalid response cannot corrupt bidding behavior.
9. As a Bidder, I want AI advice to use fresh Auction context, so that it does not reason from an old Bid event.
10. As a Bidder, I want AI advice to respect my configured preference, so that the judgement reflects my stated intent.
11. As a Bidder, I want rules-only behavior to remain available without an AI API key, so that local development and basic use remain inexpensive.
12. As a Bidder, I want an amount equal to my Budget to remain eligible, so that AI integration does not change the inclusive Budget boundary.
13. As a Bidder, I want a proposed amount above my Budget rejected before AI is called, so that sensitive or invalid decisions are not delegated.
14. As a Bidder, I want AI skipped when I already lead, so that the model cannot cause bidding against myself.
15. As a Bidder, I want AI skipped when the Auction is not open, so that the model cannot influence a late Bid.
16. As a Bidder, I want AI skipped when my Proxy Bidder is paused or completed, so that its lifecycle remains authoritative.
17. As a Bidder, I want the model provider to be replaceable, so that I am not dependent on Gemini or one commercial vendor.
18. As a developer, I want a local open-weight model option, so that I can develop without per-request API charges.
19. As a developer, I want one provider interface, so that Groq, Ollama, or a future Gemini adapter does not leak into domain logic.
20. As a developer, I want a structured response schema, so that free-form model prose never controls program flow.
21. As a developer, I want deterministic, low-temperature inference, so that the same context is less likely to produce inconsistent advice.
22. As a developer, I want no AI SDK requirement in the domain layer, so that provider libraries remain infrastructure details.
23. As an operator, I want provider, model, latency, outcome, and fallback status logged, so that AI behavior can be diagnosed.
24. As an operator, I want the Reaction identifier propagated into AI logs, so that one autonomous decision can be traced across services.
25. As an operator, I want model timeouts and circuit-breaker state visible, so that degraded AI behavior is distinguishable from Auction failures.
26. As an operator, I want AI API keys supplied only through environment secrets, so that credentials never enter Git or configuration files.
27. As an operator, I want provider requests bounded by timeout and output length, so that one inference cannot consume unbounded resources.
28. As an operator, I want raw prompts and model responses excluded from normal logs, so that Budget and preference data are not exposed.
29. As an operator, I want no Bidder identity, JWT, contact details, or Deal data sent to the model, so that the provider receives only necessary decision context.
30. As an operator, I want repeated advice calls to be harmless, so that a retry cannot create a Bid or mutate Auction state.
31. As a security engineer, I want the advice endpoint accessible only to authenticated `agentservice`, so that clients cannot invoke the model directly.
32. As a security engineer, I want untrusted Item text treated as data, so that Item content cannot override the fixed system instruction.
33. As a security engineer, I want provider tool use and web access disabled, so that advice cannot perform external actions.
34. As a system, I want `agentservice` to remain the only component that decides whether to submit the rules-produced command, so that orchestration stays explicit.
35. As a system, I want `auctionservice` to remain the sole Bid authority, so that AI gains no direct Auction access.
36. As a system, I want an AI `BID` recommendation to use the existing proposed amount unchanged, so that model output cannot alter money.
37. As a system, I want an AI `SKIP` recommendation recorded as a skipped Reaction, so that no Auction command is sent.
38. As a system, I want an unavailable AI provider to produce a rules fallback rather than a failed Reaction, so that advisory failure is not confused with bidding failure.
39. As a system, I want AI advice to remain synchronous but tightly time-bounded, so that it fits the existing synchronous Bid placement flow.
40. As a future maintainer, I want Gemini addable as another adapter, so that provider comparison does not require changing application behavior.

## Implementation Decisions

- **Primary module:** `aiservice` owns prompt construction, provider invocation, structured-output validation, timeout handling, circuit breaking, and provider observability.
- **Calling module:** `agentservice` owns the optional AI-assisted mode, collects the minimum decision context, calls `aiservice`, applies fallback, and continues to own Reaction state and Bid orchestration.
- **Authority boundary:** AI is called only after fresh Auction state passes lifecycle, ownership, minimum-increment, and Budget checks. It receives the already-calculated proposed amount and cannot return an amount.
- **Internal API:** `aiservice` exposes one service-authenticated internal advice operation. It is not exposed through the public gateway.
- **Request contract:** the request carries a Reaction identifier, Auction identifier, sanitized Item context, current price, proposed amount, Budget, and a bounded Bidder preference. It carries no Bidder identifier, JWT, contact information, Deal data, or payment data.
- **Response contract:** the response carries a recommendation enum (`BID` or `SKIP`), a stable reason code, and a short human-readable reason. Provider metadata remains internal observability data rather than business authority.
- **AI-assisted mode:** a Proxy Bidder explicitly selects `RULES_ONLY` or `AI_ASSISTED`. Existing Proxy Bidders default to `RULES_ONLY`.
- **Rules fallback:** timeout, open circuit, rate limit, provider `5xx`, connection failure, invalid JSON, missing fields, unknown enum values, or unsafe output all resolve to `BID` through the existing rules-first path. AI failure does not mark the Reaction failed.
- **AI skip:** a valid `SKIP` recommendation prevents the Auction command and records the Reaction as skipped with an AI-specific reason code.
- **No AI amount:** model output containing an amount or instruction to change an amount is ignored. Only the validated recommendation and bounded reason are accepted.
- **Provider interface:** application logic depends on one `AiAdviceProvider` abstraction. Provider-specific HTTP shapes, authentication, and model names remain in adapters.
- **Hosted V1 provider:** Groq with `llama-3.1-8b-instant` is the default hosted adapter because the task is a small structured classification, the provider offers a free developer tier, and paid token prices are very low.
- **Local provider:** Ollama with a configured Qwen3 instruct model is supported for local development. Qwen3 weights are Apache-2.0, and Ollama exposes a local HTTP API with JSON-schema structured outputs.
- **Gemini:** Gemini is not required for V1. A future Gemini adapter may implement the same provider interface.
- **OpenRouter:** the free router is acceptable for experiments but not the production default because model selection may vary between calls and provider data handling may vary.
- **Hugging Face:** Inference Providers remain an optional future adapter. The small free monthly credit is useful for evaluation, not a dependable production free tier.
- **Inference settings:** use structured JSON output, temperature zero or the provider's lowest deterministic setting, a small output-token limit, no streaming, no tools, and no provider-side web search.
- **Prompt safety:** a fixed system instruction states the narrow classification task and treats Item and preference strings as quoted data. Inputs are length-bounded and control characters are normalized.
- **Resilience:** `aiservice` wraps the provider adapter with Resilience4j timeout and circuit breaker. `agentservice` also applies a short total HTTP timeout and owns the final rules fallback.
- **No persistence in `aiservice`:** V1 is stateless and has no Postgres, Redis, RabbitMQ, or model-response database.
- **No provider retry in the Bid path:** a time-sensitive advice call is attempted once. A failed call falls back immediately rather than consuming the Auction window with retries.
- **Authentication:** Keycloak uses service identity. `aiservice` accepts only a token issued for the authenticated `agentservice` client and the `aiservice` audience. No new human-facing realm role is added.
- **Secrets:** provider API keys are environment variables or deployment secrets. Local Ollama requires no API key.
- **Logs and metrics:** record Reaction ID, provider, model, latency, recommendation, reason code, fallback reason, timeout count, circuit state, and malformed-response count. Never log Budget, full prompts, full responses, JWTs, or API keys.
- **Provider-neutral configuration:** provider type, base URL, model, timeout, and output-token bound are external configuration. Business services do not branch on provider names.
- **Cost guard:** production configuration has an explicit provider choice and request timeout. Hosted providers use account-side spend limits when available. Falling back to rules is always safer than failing the bidding path.

## Testing Decisions

- **Good test:** assert behavior at the highest external boundary. Verify whether an Auction command is or is not submitted; do not assert prompt-builder private methods, provider SDK calls, Resilience4j internals, or Spring bean names.
- **Primary seam:** drive a Proxy Bidder Reaction through `agentservice`, stub the authenticated `aiservice` HTTP boundary, and observe the existing authenticated Auction command boundary.
- **AI service seam:** drive the authenticated internal advice endpoint while replacing `AiAdviceProvider` with a deterministic stub. Assert the stable advice contract and security behavior.
- **No live model in CI:** automated tests never call Groq, Ollama Cloud, Gemini, OpenRouter, or Hugging Face. Provider adapter contract tests use a controllable HTTP stub.
- **Rules-only behavior:** a `RULES_ONLY` Proxy Bidder never calls `aiservice` and keeps the current Reaction behavior.
- **AI bid behavior:** a valid `BID` recommendation causes the unchanged rules-produced amount to reach the normal Auction command.
- **AI skip behavior:** a valid `SKIP` recommendation sends no Auction command and records a skipped Reaction with the advice reason code.
- **Budget boundary:** an amount above Budget is skipped before any AI request; an amount equal to Budget may reach AI and, if recommended, the Auction command.
- **Fallback behavior:** timeout, connection refusal, open circuit, `429`, `5xx`, malformed JSON, invalid enum, or oversized response all fall back to the rules-produced Bid.
- **Security behavior:** missing token returns `401`; a human token or wrong service client returns `403`; the authenticated `agentservice` client succeeds.
- **Privacy behavior:** provider adapter requests omit Bidder identity, contact details, Deal data, JWTs, and payment data.
- **Prompt-injection behavior:** hostile Item text cannot change the response schema, request tools, select an amount, or bypass fixed instructions.
- **Observability behavior:** terminal advice and fallback paths emit identifiers and outcome metadata without logging Budget or full prompt content.
- **Prior art:** follow the internal service-authentication seam used between `agentservice` and `auctionservice`, and the external-behavior testing direction in the Proxy Bidder and Auction Bid core specs.

## Out of Scope

- Allowing AI to calculate, raise, lower, or otherwise modify a Bid amount.
- Allowing AI to override Budget, Auction status, ownership, minimum increment, optimistic locking, or Seller restrictions.
- Direct communication between `aiservice` and `auctionservice`.
- Public AI endpoints or direct browser access to model providers.
- Model training, fine-tuning, embeddings, vector databases, retrieval-augmented generation, or long-term model memory.
- Provider tool use, web search, code execution, file access, or autonomous external actions.
- Storing complete prompts or model responses.
- Automatic provider retries in the synchronous Bid path.
- Paying for or operating dedicated GPU inference in V1.
- Claiming that one model is universally better than Gemini; selection is based on this narrow structured-classification task, cost, latency, and replaceability.
- Replacing the rules-first Proxy Bidder strategy.
- Completing the agent-vs-agent integration evidence from the previous Proxy Bidder ticket.

## Further Notes

- Current provider prices and free-tier limits are operational inputs, not permanent architecture. They must be rechecked before production deployment.
- Groq currently documents a free tier and prices `llama-3.1-8b-instant` at very low per-token rates, making it a practical hosted default for this narrow task.
- Ollama serves a local API by default and supports JSON-schema structured outputs. Local inference has no per-request API charge but still consumes developer hardware and, if deployed, cloud compute.
- Qwen3 publishes multiple open-weight sizes under Apache 2.0. A small instruct variant is appropriate for local development; the exact quantization is a machine-level configuration choice.
- Gemini itself currently offers free-tier models. It remains a reasonable provider option, but the architecture does not require it.
- “Free” hosted inference always carries rate, availability, privacy, or model-selection constraints. Production behavior must rely on the rules fallback, not on continued free access.
- Coding remains developer-owned. This specification defines the behavior, authority boundaries, provider seam, and test seam for implementation.
