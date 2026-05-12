package pay.conflux.backend.paymentcore.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyRecordIdTest {

  @Test
  void equals_reflexive() {
    IdempotencyRecordId id = new IdempotencyRecordId(UUID.randomUUID(), "key-1");
    assertThat(id).isEqualTo(id);
  }

  @Test
  void equals_sameValues_areEqual() {
    UUID businessId = UUID.randomUUID();
    IdempotencyRecordId a = new IdempotencyRecordId(businessId, "key-1");
    IdempotencyRecordId b = new IdempotencyRecordId(businessId, "key-1");
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void equals_differentBusinessId_areNotEqual() {
    IdempotencyRecordId a = new IdempotencyRecordId(UUID.randomUUID(), "key-1");
    IdempotencyRecordId b = new IdempotencyRecordId(UUID.randomUUID(), "key-1");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentRequestKey_areNotEqual() {
    UUID businessId = UUID.randomUUID();
    IdempotencyRecordId a = new IdempotencyRecordId(businessId, "key-1");
    IdempotencyRecordId b = new IdempotencyRecordId(businessId, "key-2");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentType_isFalse() {
    IdempotencyRecordId id = new IdempotencyRecordId(UUID.randomUUID(), "k");
    assertThat(id).isNotEqualTo("not-a-record-id");
    assertThat(id).isNotEqualTo(null);
  }
}
