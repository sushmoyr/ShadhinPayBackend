package pay.conflux.backend.common.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.hibernate.annotations.FilterDef;
import org.junit.jupiter.api.Test;

class TenantFilterDefTest {

  @Test
  void constants_matchSpec() {
    assertThat(TenantFilterDef.NAME).isEqualTo("tenantFilter");
    assertThat(TenantFilterDef.PARAM_BUSINESS_ID).isEqualTo("businessId");
  }

  @Test
  void filterDefAnnotation_isPresentAndDeclaresUuidParam() {
    FilterDef def = TenantFilterDef.class.getAnnotation(FilterDef.class);
    assertThat(def).isNotNull();
    assertThat(def.name()).isEqualTo("tenantFilter");
    assertThat(def.parameters()).hasSize(1);
    assertThat(def.parameters()[0].name()).isEqualTo("businessId");
    assertThat(def.parameters()[0].type()).isEqualTo(UUID.class);
  }
}
