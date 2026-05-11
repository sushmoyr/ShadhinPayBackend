package pay.conflux.backend.common.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.common.security.AuthenticatedPrincipal.Environment;
import pay.conflux.backend.common.security.AuthenticatedPrincipal.UserType;

class TenantInterceptorTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void preHandle_enablesFilterWhenBusinessIdPresent() {
    UUID businessId = UUID.randomUUID();
    setMerchant(businessId);

    EntityManager em = mock(EntityManager.class);
    Session session = mock(Session.class);
    Filter filter = mock(Filter.class);
    when(em.unwrap(Session.class)).thenReturn(session);
    when(session.enableFilter(TenantFilterDef.NAME)).thenReturn(filter);
    when(filter.setParameter(TenantFilterDef.PARAM_BUSINESS_ID, businessId)).thenReturn(filter);

    TenantInterceptor interceptor = new TenantInterceptor(em);
    boolean result =
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), this);

    assertThat(result).isTrue();
    verify(session, times(1)).enableFilter(TenantFilterDef.NAME);
    verify(filter, times(1)).setParameter(TenantFilterDef.PARAM_BUSINESS_ID, businessId);
  }

  @Test
  void preHandle_skipsFilterWhenNoBusinessContext() {
    EntityManager em = mock(EntityManager.class);

    TenantInterceptor interceptor = new TenantInterceptor(em);
    boolean result =
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), this);

    assertThat(result).isTrue();
    verify(em, never()).unwrap(Session.class);
  }

  @Test
  void afterCompletion_disablesFilter() {
    EntityManager em = mock(EntityManager.class);
    Session session = mock(Session.class);
    when(em.unwrap(Session.class)).thenReturn(session);

    TenantInterceptor interceptor = new TenantInterceptor(em);
    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), this, null);

    verify(session, times(1)).disableFilter(TenantFilterDef.NAME);
  }

  @Test
  void afterCompletion_swallowsIllegalState() {
    EntityManager em = mock(EntityManager.class);
    when(em.unwrap(Session.class)).thenThrow(new IllegalStateException("session closed"));

    TenantInterceptor interceptor = new TenantInterceptor(em);
    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), this, null);
  }

  private static void setMerchant(UUID businessId) {
    var principal =
        new AuthenticatedPrincipal(
            UUID.randomUUID(), UserType.MERCHANT, UUID.randomUUID(), businessId, Environment.LIVE);
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
