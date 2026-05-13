package pay.conflux.backend.identity.enums;

/**
 * Refines {@code User.userType = ADMIN} into the three admin tiers from {@code
 * DOCS/features/identity/PRD.md §4.3}. Higher tiers strictly inherit lower-tier authorities — a
 * {@link #MANAGER} carries every {@link #VIEWER} authority, and a {@link #SUPER} carries every
 * {@link #MANAGER} authority. Granted Spring authorities are resolved by the Wave D auth filter per
 * {@code DOCS/features/identity/TECH_SPEC.md §4.3}.
 */
public enum AdminTier {
  VIEWER,
  MANAGER,
  SUPER
}
