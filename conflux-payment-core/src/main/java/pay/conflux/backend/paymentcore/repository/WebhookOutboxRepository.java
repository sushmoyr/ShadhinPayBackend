package pay.conflux.backend.paymentcore.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;

@Repository
public interface WebhookOutboxRepository extends JpaRepository<WebhookOutbox, UUID> {

  List<WebhookOutbox> findAllByStatusAndNextAttemptAtBefore(
      WebhookOutboxStatus status, Instant threshold, Pageable pageable);
}
