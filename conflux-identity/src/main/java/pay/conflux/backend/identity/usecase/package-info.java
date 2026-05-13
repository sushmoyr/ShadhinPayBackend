/**
 * Inbound use-case ports exposed by the Identity module.
 *
 * <p>Marked as a Spring Modulith named interface so other modules may depend on these contracts;
 * types in sibling internal packages ({@code entity}, {@code repository}, {@code mapper}, {@code
 * support}, {@code usecase.impl}) remain off-limits to outside modules.
 */
@org.springframework.modulith.NamedInterface("usecase")
package pay.conflux.backend.identity.usecase;
