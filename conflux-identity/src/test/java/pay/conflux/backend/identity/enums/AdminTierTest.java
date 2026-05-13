package pay.conflux.backend.identity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Hardcoded values + count tripwire on {@link AdminTier}. Any change to the enum (add/remove a
 * tier, reorder, rename) breaks this test, forcing the next change to be deliberate and to revisit
 * the authority-inheritance matrix in {@code DOCS/features/identity/TECH_SPEC.md §4.3}.
 */
class AdminTierTest {

  @Test
  void values_areExactlyViewerManagerSuperInOrder() {
    assertThat(AdminTier.values())
        .containsExactly(AdminTier.VIEWER, AdminTier.MANAGER, AdminTier.SUPER);
  }

  @Test
  void values_hasExactlyThreeTiers() {
    assertThat(AdminTier.values()).hasSize(3);
  }
}
