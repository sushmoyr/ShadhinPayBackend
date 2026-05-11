package pay.conflux.backend.application.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines the platform-wide OpenAPI document. Frontend agents and SDK generators consume this via
 * the JSON dump at {@code DOCS/contracts/openapi.json}; the live document is served at {@code
 * /v3/api-docs}.
 *
 * <p>The {@code ApiKeyAuth} security scheme is wired as a global default — every endpoint inherits
 * it unless its operation overrides with {@code @SecurityRequirements({})}. The exact tenant-key
 * header semantics are finalised in Phase 1; for now this is a placeholder bearer-token scheme so
 * the frozen contract already shows the auth surface.
 */
@Configuration
public class OpenApiConfig {

  private static final String API_KEY_SCHEME = "ApiKeyAuth";

  @Bean
  public OpenAPI shadhinPayOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ConfluxPay API")
                .version("v1")
                .description(
                    "ConfluxPay backend API — multi-tenant payment platform. All requests require a"
                        + " tenant API key carried in the Authorization header.")
                .contact(
                    new Contact().name("ConfluxPay Engineering").email("engineering@conflux.com"))
                .license(new License().name("Proprietary").url("https://conflux.com/license")))
        .components(
            new Components()
                .addSecuritySchemes(
                    API_KEY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("API_KEY")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description(
                            "Tenant API key. Send as `Authorization: Bearer <api-key>`. "
                                + "Keys are issued per tenant in Phase 1; rotation invalidates "
                                + "previous keys immediately.")))
        .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
  }
}
