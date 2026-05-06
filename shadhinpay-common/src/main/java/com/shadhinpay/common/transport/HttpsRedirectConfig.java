package com.shadhinpay.common.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class HttpsRedirectConfig {

  public static final String HSTS_HEADER = "Strict-Transport-Security";
  public static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";

  @Bean
  public FilterRegistrationBean<HstsFilter> hstsFilterRegistration() {
    FilterRegistrationBean<HstsFilter> bean = new FilterRegistrationBean<>(new HstsFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    bean.addUrlPatterns("/*");
    return bean;
  }

  static class HstsFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      response.setHeader(HSTS_HEADER, HSTS_VALUE);
      chain.doFilter(request, response);
    }
  }
}
