/**
 * Outbound port for MFS adapters.
 *
 * <p>Marked as a Spring Modulith named interface so {@code payment-core} (and other future callers)
 * may depend on the {@link pay.conflux.backend.adapters.port.PaymentProvider} contract and its
 * DTOs. Concrete adapter implementations live in private sibling packages (e.g. {@code mock},
 * {@code bkash}, {@code stripe}) and remain off-limits to outside modules.
 */
@org.springframework.modulith.NamedInterface("port")
package pay.conflux.backend.adapters.port;
