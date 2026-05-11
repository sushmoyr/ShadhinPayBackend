package pay.conflux.backend.common.tenancy;

import java.util.UUID;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Declares the global Hibernate {@code tenantFilter} used for passive multi-tenant isolation.
 *
 * <p>Feature modules annotate tenant-scoped entities with both the matching {@link
 * org.hibernate.annotations.Filter} and the {@link org.hibernate.annotations.FilterDef} reference
 * imported from this package. Example (commented marker):
 *
 * <pre>
 * &#064;FilterDef(name = "tenantFilter", parameters = &#064;ParamDef(name = "businessId", type = UUID.class))
 * &#064;Filter(name = "tenantFilter", condition = "business_id = :businessId")
 * &#064;Entity
 * public class TenantScopedExample {
 *   private UUID businessId;
 * }
 * </pre>
 *
 * The runtime activation lives in {@link TenantInterceptor}.
 */
@FilterDef(
    name = TenantFilterDef.NAME,
    parameters = @ParamDef(name = TenantFilterDef.PARAM_BUSINESS_ID, type = UUID.class))
public final class TenantFilterDef {

  public static final String NAME = "tenantFilter";
  public static final String PARAM_BUSINESS_ID = "businessId";

  private TenantFilterDef() {}
}
