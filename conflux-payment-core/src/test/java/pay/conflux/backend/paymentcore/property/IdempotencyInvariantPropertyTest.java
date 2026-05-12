package pay.conflux.backend.paymentcore.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.paymentcore.entity.IdempotencyRecord;
import pay.conflux.backend.paymentcore.entity.IdempotencyRecordId;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
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
 * Idempotency invariant: for any N ∈ [1..50] sequential {@code execute(…)} invocations sharing the
 * same {@code (businessId, requestKey)}, the orchestrator persists exactly one {@code Transaction},
 * one {@code IdempotencyRecord}, and one {@code WebhookOutbox} regardless of N. The first call runs
 * the full pipeline; every replay returns the cached payload and does not touch the downstream
 * collaborators.
 */
class IdempotencyInvariantPropertyTest {

  private TransactionRepository transactionRepository;
  private IdempotencyRecordRepository idempotencyRepository;
  private WebhookOutboxRepository webhookOutboxRepository;
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

  private final ConcurrentHashMap<UUID, Transaction> transactionStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<IdempotencyRecordId, IdempotencyRecord> idempotencyStore =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, WebhookOutbox> webhookStore = new ConcurrentHashMap<>();
  private final AtomicInteger adapterCallCount = new AtomicInteger();

  @BeforeProperty
  void initEach() {
    transactionStore.clear();
    idempotencyStore.clear();
    webhookStore.clear();
    adapterCallCount.set(0);

    transactionRepository = Mockito.mock(TransactionRepository.class);
    idempotencyRepository = Mockito.mock(IdempotencyRecordRepository.class);
    webhookOutboxRepository = Mockito.mock(WebhookOutboxRepository.class);
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
    Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    Mockito.lenient().when(valueOperations.get(Mockito.anyString())).thenReturn(null);

    Mockito.when(transactionRepository.save(Mockito.any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              if (t.getId() == null) {
                t.setId(UUID.randomUUID());
              }
              transactionStore.put(t.getId(), t);
              return t;
            });
    Mockito.when(webhookOutboxRepository.save(Mockito.any(WebhookOutbox.class)))
        .thenAnswer(
            inv -> {
              WebhookOutbox w = inv.getArgument(0);
              if (w.getId() == null) {
                w.setId(UUID.randomUUID());
              }
              webhookStore.put(w.getId(), w);
              return w;
            });
    Mockito.when(idempotencyRepository.save(Mockito.any(IdempotencyRecord.class)))
        .thenAnswer(
            inv -> {
              IdempotencyRecord r = inv.getArgument(0);
              idempotencyStore.put(r.getId(), r);
              return r;
            });
    Mockito.when(
            idempotencyRepository.findByBusinessIdAndRequestKey(
                Mockito.any(UUID.class), Mockito.anyString()))
        .thenAnswer(
            inv -> {
              UUID bid = inv.getArgument(0);
              String key = inv.getArgument(1);
              IdempotencyRecord found = idempotencyStore.get(new IdempotencyRecordId(bid, key));
              if (found != null && !found.getExpiresAt().isBefore(Instant.now())) {
                return Optional.of(found);
              }
              return Optional.empty();
            });

    Mockito.when(getVendorConfigUseCase.execute(Mockito.any(), Mockito.anyString()))
        .thenReturn(new VendorConfigDescriptor("MOCK", "PARTNER", Map.of()));
    Mockito.when(evaluateTransactionUseCase.execute(Mockito.any()))
        .thenReturn(new RiskDecision(RiskDecision.Action.ALLOW, 0, List.of(), "ok"));

    PaymentProvider provider = Mockito.mock(PaymentProvider.class);
    Mockito.when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    Mockito.when(credentialsResolver.resolveCredentials(Mockito.any(), Mockito.anyString()))
        .thenReturn(Map.of("k", "v"));
    Mockito.when(
            provider.initiate(
                Mockito.any(VendorPaymentRequest.class), Mockito.any(VendorCredentials.class)))
        .thenAnswer(
            inv -> {
              adapterCallCount.incrementAndGet();
              return new VendorResponse(VendorStatus.PENDING, "vtx", "https://r", null, null);
            });

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
            idempotencyRepository,
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
  void resetStateBetweenTries() {
    transactionStore.clear();
    idempotencyStore.clear();
    webhookStore.clear();
    adapterCallCount.set(0);
  }

  @AfterProperty
  void teardown() {
    SecurityContextHolder.clearContext();
  }

  @Property(tries = 500)
  void nReplayCallsWithSameKey_persistExactlyOneTransaction(
      @ForAll @IntRange(min = 1, max = 50) int n, @ForAll("requestKeys") String requestKey) {
    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            businessId,
            new Money(new BigDecimal("100.00"), "BDT"),
            "MOCK",
            "order-1",
            "https://merchant/return",
            "https://merchant/webhook",
            Map.of(),
            requestKey);

    for (int i = 0; i < n; i++) {
      useCase.execute(req);
    }

    assertThat(transactionStore.size())
        .as("exactly one Transaction row for N=%d sequential replays", n)
        .isEqualTo(1);
    assertThat(idempotencyStore.size())
        .as("exactly one IdempotencyRecord for N=%d sequential replays", n)
        .isEqualTo(1);
    assertThat(webhookStore.size())
        .as("exactly one WebhookOutbox row for N=%d sequential replays", n)
        .isEqualTo(1);
    assertThat(adapterCallCount.get())
        .as("vendor adapter dispatched exactly once across N=%d replays", n)
        .isEqualTo(1);
  }

  @Provide
  Arbitrary<String> requestKeys() {
    return Arbitraries.strings()
        .withCharRange('a', 'z')
        .ofMinLength(8)
        .ofMaxLength(32)
        .filter(s -> !s.isBlank());
  }
}
