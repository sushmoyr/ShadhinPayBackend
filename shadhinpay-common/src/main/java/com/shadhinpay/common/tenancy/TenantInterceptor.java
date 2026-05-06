package com.shadhinpay.common.tenancy;

import com.shadhinpay.common.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class TenantInterceptor implements HandlerInterceptor {

  private final EntityManager entityManager;

  public TenantInterceptor(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    UUID businessId = SecurityUtils.currentBusinessId().orElse(null);
    if (businessId == null) {
      return true;
    }
    Session session = entityManager.unwrap(Session.class);
    session
        .enableFilter(TenantFilterDef.NAME)
        .setParameter(TenantFilterDef.PARAM_BUSINESS_ID, businessId);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    try {
      Session session = entityManager.unwrap(Session.class);
      session.disableFilter(TenantFilterDef.NAME);
    } catch (IllegalStateException ignored) {
      // Session no longer active; nothing to disable.
    }
  }

  @Configuration
  static class Registration implements WebMvcConfigurer {

    private final TenantInterceptor interceptor;

    @Autowired
    Registration(TenantInterceptor interceptor) {
      this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(interceptor);
    }
  }
}
