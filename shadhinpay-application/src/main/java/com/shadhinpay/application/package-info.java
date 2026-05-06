/**
 * Application bootstrap layer — Spring {@code @Configuration} beans that wire cross-cutting
 * infrastructure (OpenAPI document, security adapters, observability) onto the booted application.
 *
 * <p>This module contains no domain logic; feature modules MUST NOT depend on it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Application Bootstrap")
package com.shadhinpay.application;
