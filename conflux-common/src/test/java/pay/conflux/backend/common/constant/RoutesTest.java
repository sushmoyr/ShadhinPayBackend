package pay.conflux.backend.common.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutesTest {

  @Test
  void v1Base_isApiV1() {
    assertThat(Routes.V1.BASE).isEqualTo("/api/v1");
  }

  @Test
  void adminBase_nestsUnderV1() {
    assertThat(Routes.V1.Admin.BASE).isEqualTo("/api/v1/admin");
  }

  @Test
  void publicBase_matchesV1() {
    assertThat(Routes.V1.Public.BASE).isEqualTo("/api/v1");
  }
}
