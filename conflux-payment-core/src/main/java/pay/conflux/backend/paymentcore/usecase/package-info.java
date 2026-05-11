/**
 * Inbound use-case ports exposed by the Payment Core module.
 *
 * <p>Marked as a Spring Modulith named interface so other modules ({@code invoice}, public REST
 * controllers in {@code application}) may depend on it; sibling internal packages ({@code entity},
 * {@code repository}, {@code mapper}, {@code usecase.impl}) remain off-limits to outside modules.
 */
@org.springframework.modulith.NamedInterface("usecase")
package pay.conflux.backend.paymentcore.usecase;
