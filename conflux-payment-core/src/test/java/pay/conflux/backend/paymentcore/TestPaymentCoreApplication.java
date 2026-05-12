package pay.conflux.backend.paymentcore;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal {@code @SpringBootApplication} root that {@code @WebMvcTest} slice tests boot against.
 * The slice's {@code TypeExcludeFilter} restricts component scanning to controllers under this
 * package, so production beans (repositories, JPA, Redis, listeners) are not wired into the slice.
 */
@SpringBootApplication
public class TestPaymentCoreApplication {}
