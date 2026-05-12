package pay.conflux.backend.paymentcore.property;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.paymentcore.error.RiskRejectedException;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.impl.InitiatePaymentUseCaseImpl;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;
import pay.conflux.backend.provisioning.usecase.GetVendorConfigUseCase;
import pay.conflux.backend.provisioning.usecase.VendorConfigDescriptor;
import pay.conflux.backend.quota.usecase.ConfirmQuotaUseCase;
import pay.conflux.backend.quota.usecase.ReleaseQuotaUseCase;
import pay.conflux.backend.quota.usecase.ReserveQuotaUseCase;
import pay.conflux.backend.risk.usecase.EvaluateTransactionUseCase;
import pay.conflux.backend.risk.usecase.RiskDecision;

/**
 * Orchestration rollback invariant: for any call whose risk evaluation aborts the orchestration
 * (the engine threw, or returned {@code BLOCK}), nothing is persisted — {@code Transaction}, {@code
 * IdempotencyRecord}, and {@code WebhookOutbox} repositories receive zero writes.
 */
class OrchestrationRollbackPropertyTest {

  private TransactionRepository transactionRepository;
  private WebhookOutboxRepository webhookOutboxRepository;
  private IdempotencyRecordRepository idempotencyRecordRepository;
  private GetVendorConfigUseCase getVendorConfigUseCase;
  private CredentialsResolver credentialsResolver;
  private EvaluateTransactionUseCase evaluateTransactionUseCase;
  private ReserveQuotaUseCase reserveQuotaUseCase;
  private ConfirmQuotaUseCase confirmQuotaUseCase;
  private ReleaseQuotaUseCase releaseQuotaUseCase;
  private PaymentProviderRegistry paymentProviderRegistry;
  private ApplicationEventPublisher eventPublisher;
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private InitiatePaymentUseCaseImpl useCase;

  private UUID businessId;
  private UUID merchantId;

  @BeforeProperty
  void initEach() {
    transactionRepository = Mockito.mock(TransactionRepository.class);
    webhookOutboxRepository = Mockito.mock(WebhookOutboxRepository.class);
    idempotencyRecordRepository = Mockito.mock(IdempotencyRecordRepository.class);
    getVendorConfigUseCase = Mockito.mock(GetVendorConfigUseCase.class);
    credentialsResolver = Mockito.mock(CredentialsResolver.class);
    evaluateTransactionUseCase = Mockito.mock(EvaluateTransactionUseCase.class);
    reserveQuotaUseCase = Mockito.mock(ReserveQuotaUseCase.class);
    confirmQuotaUseCase = Mockito.mock(ConfirmQuotaUseCase.class);
    releaseQuotaUseCase = Mockito.mock(ReleaseQuotaUseCase.class);
    paymentProviderRegistry = Mockito.mock(PaymentProviderRegistry.class);
    eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    redisTemplate = Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> v = Mockito.mock(ValueOperations.class);
    valueOperations = v;
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(valueOperations.get(anyString())).thenReturn(null);

    businessId = UUID.randomUUID();
    merchantId = UUID.randomUUID();

    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            merchantId,
            AuthenticatedPrincipal.UserType.MERCHANT,
            merchantId,
            businessId,
            AuthenticatedPrincipal.Environment.TEST);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

    useCase =
        new InitiatePaymentUseCaseImpl(
            transactionRepository,
            webhookOutboxRepository,
            idempotencyRecordRepository,
            getVendorConfigUseCase,
            credentialsResolver,
            evaluateTransactionUseCase,
            reserveQuotaUseCase,
            confirmQuotaUseCase,
            releaseQuotaUseCase,
            paymentProviderRegistry,
            eventPublisher,
            redisTemplate,
            new ObjectMapper());
  }

  @BeforeTry
  void resetMocks() {
    reset(
        transactionRepository,
        webhookOutboxRepository,
        idempotencyRecordRepository,
        getVendorConfigUseCase,
        evaluateTransactionUseCase,
        paymentProviderRegistry,
        reserveQuotaUseCase);
    doReturn(new VendorConfigDescriptor("MOCK", "PARTNER", Map.of()))
        .when(getVendorConfigUseCase)
        .execute(any(), anyString());
  }

  @Property(tries = 500)
  void riskBlock_neverPersistsAnything(
      @ForAll("amounts") BigDecimal amount, @ForAll("orderRefs") String orderRef) {
    doReturn(new RiskDecision(RiskDecision.Action.BLOCK, 100, List.of(), "blocked"))
        .when(evaluateTransactionUseCase)
        .execute(any());

    assertThatThrownBy(() -> useCase.execute(buildRequest(amount, orderRef)))
        .isInstanceOf(RiskRejectedException.class);

    verify(transactionRepository, never()).save(any());
    verify(idempotencyRecordRepository, never()).save(any());
    verify(webhookOutboxRepository, never()).save(any());
    verifyNoInteractions(paymentProviderRegistry, reserveQuotaUseCase);
  }

  @Property(tries = 500)
  void riskThrows_failsClosedAndPersistsNothing(
      @ForAll("amounts") BigDecimal amount, @ForAll("orderRefs") String orderRef) {
    doThrow(new RuntimeException("risk engine down"))
        .when(evaluateTransactionUseCase)
        .execute(any());

    assertThatThrownBy(() -> useCase.execute(buildRequest(amount, orderRef)))
        .isInstanceOf(RiskRejectedException.class);

    verify(transactionRepository, never()).save(any());
    verify(idempotencyRecordRepository, never()).save(any());
    verify(webhookOutboxRepository, never()).save(any());
    verifyNoInteractions(paymentProviderRegistry, reserveQuotaUseCase);
  }

  private InitiatePaymentRequest buildRequest(BigDecimal amount, String orderRef) {
    return new InitiatePaymentRequest(
        businessId,
        new Money(amount, "BDT"),
        "MOCK",
        orderRef,
        "https://merchant/return",
        "https://merchant/webhook",
        Map.of(),
        "idem-" + orderRef);
  }

  @Provide
  Arbitrary<BigDecimal> amounts() {
    return Arbitraries.integers().between(1, 1_000_000).map(BigDecimal::valueOf);
  }

  @Provide
  Arbitrary<String> orderRefs() {
    return Arbitraries.strings()
        .withCharRange('a', 'z')
        .ofMinLength(1)
        .ofMaxLength(32)
        .filter(s -> !s.isBlank());
  }
}
