/**
 * Internal use-case implementations for payment-core. Marked as a Modulith named interface so
 * Spring Modulith does not surface these to outside modules; only the {@code
 * pay.conflux.backend.paymentcore.usecase} package (which carries the {@code "usecase"} named
 * interface) is externally consumable.
 */
@org.springframework.modulith.NamedInterface("impl")
package pay.conflux.backend.paymentcore.usecase.impl;
