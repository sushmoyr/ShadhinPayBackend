package pay.conflux.backend.paymentcore.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;

@Repository
public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByVendorTransactionId(String vendorTransactionId);

  List<Transaction> findAllByStatusAndUpdatedAtBefore(
      TransactionStatus status, LocalDateTime threshold, Pageable pageable);
}
