package com.shadhinpay.quota.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shadhinpay.quota.cache.QuotaCachePort;
import com.shadhinpay.quota.config.QuotaConfig;
import com.shadhinpay.quota.usecase.QuotaReservation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class QuotaInvariantPropertyTest {

  private static class InMemoryCachePort implements QuotaCachePort {
    private final ConcurrentHashMap<String, String> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> finals = new ConcurrentHashMap<>();

    @Override
    public String reservePending(UUID merchantId, String period) {
      String id = UUID.randomUUID().toString();
      pending.put(id, period);
      return id;
    }

    @Override
    public boolean releasePending(UUID merchantId, String period, String reservationId) {
      return pending.remove(reservationId) != null;
    }

    @Override
    public boolean confirmReservation(UUID merchantId, String period, String reservationId) {
      if (pending.remove(reservationId) != null) {
        finals
            .computeIfAbsent(merchantId + ":" + period, k -> new AtomicInteger(0))
            .incrementAndGet();
        return true;
      }
      return false;
    }

    @Override
    public int getFinalCount(UUID merchantId, String period) {
      AtomicInteger count = finals.get(merchantId + ":" + period);
      return count == null ? 0 : count.get();
    }

    @Override
    public int countPending(UUID merchantId, String period) {
      return (int) pending.values().stream().filter(p -> p.equals(period)).count();
    }
  }

  enum Operation {
    RESERVE,
    CONFIRM,
    RELEASE
  }

  @Provide
  Arbitrary<List<Operation>> operations() {
    return Arbitraries.of(Operation.class).list().ofMinSize(1).ofMaxSize(50);
  }

  @Property(tries = 200)
  void testReservationInvariant(@ForAll("operations") List<Operation> ops) {
    InMemoryCachePort cachePort = new InMemoryCachePort();
    QuotaConfig config = mock(QuotaConfig.class);
    when(config.getFreeQuotaPerMonth()).thenReturn(10);
    Clock clock = Clock.systemUTC();

    ReserveQuotaUseCaseImpl reserveUseCase = new ReserveQuotaUseCaseImpl(cachePort, config, clock);
    ConfirmQuotaUseCaseImpl confirmUseCase = new ConfirmQuotaUseCaseImpl(cachePort, clock);
    ReleaseQuotaUseCaseImpl releaseUseCase = new ReleaseQuotaUseCaseImpl(cachePort, clock);

    UUID merchantId = UUID.randomUUID();
    List<UUID> pendingReservations = new ArrayList<>();
    int issued = 0;
    int releasedOrExpired = 0;

    for (Operation op : ops) {
      switch (op) {
        case RESERVE -> {
          QuotaReservation res = reserveUseCase.execute(merchantId);
          pendingReservations.add(res.reservationId());
          issued++;
        }
        case CONFIRM -> {
          if (!pendingReservations.isEmpty()) {
            UUID resId = pendingReservations.remove(0);
            confirmUseCase.execute(merchantId, resId);
          }
        }
        case RELEASE -> {
          if (!pendingReservations.isEmpty()) {
            UUID resId = pendingReservations.remove(0);
            releaseUseCase.execute(merchantId, resId);
            releasedOrExpired++;
          }
        }
      }
    }

    String period = java.time.YearMonth.now(clock).toString();
    int finalCount = cachePort.getFinalCount(merchantId, period);
    int pendingCount = cachePort.countPending(merchantId, period);

    // final_count + pending_count == issued_reservations - released
    assertThat(finalCount + pendingCount).isEqualTo(issued - releasedOrExpired);
  }

  @Property(tries = 200)
  void testCustomModeNeverMeters(@ForAll("operations") List<Operation> ops) {
    InMemoryCachePort cachePort = new InMemoryCachePort();
    Clock clock = Clock.systemUTC();
    ConfirmQuotaUseCaseImpl confirmUseCase = new ConfirmQuotaUseCaseImpl(cachePort, clock);
    ReleaseQuotaUseCaseImpl releaseUseCase = new ReleaseQuotaUseCaseImpl(cachePort, clock);

    UUID merchantId = UUID.randomUUID();
    List<UUID> fakeReservations = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      fakeReservations.add(UUID.randomUUID());
    }

    for (Operation op : ops) {
      if (op == Operation.RESERVE) continue; // Skip reserve to simulate CUSTOM mode
      if (op == Operation.CONFIRM) confirmUseCase.execute(merchantId, fakeReservations.get(0));
      if (op == Operation.RELEASE) releaseUseCase.execute(merchantId, fakeReservations.get(0));
    }

    String period = java.time.YearMonth.now(clock).toString();
    int finalCount = cachePort.getFinalCount(merchantId, period);
    int pendingCount = cachePort.countPending(merchantId, period);

    assertThat(finalCount).isEqualTo(0);
    assertThat(pendingCount).isEqualTo(0);
  }
}
