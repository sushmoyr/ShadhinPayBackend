/**
 * Bkash Tokenized Checkout v1.2 adapter.
 *
 * <p>Exposed as a Modulith named interface because {@code payment-core}'s vendor-callback handler
 * must invoke {@code BkashAdapter.confirm(...)} (the Execute step) — a Bkash-specific public
 * surface intentionally NOT on the {@code PaymentProvider} port. See {@code
 * DOCS/prompts/wave-d/bkash-execute-wiring.md}.
 */
@org.springframework.modulith.NamedInterface("bkash")
package pay.conflux.backend.adapters.bkash;
