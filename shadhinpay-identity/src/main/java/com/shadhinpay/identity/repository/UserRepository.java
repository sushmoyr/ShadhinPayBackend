package com.shadhinpay.identity.repository;

import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.IdentifierType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByIdentifierAndIdentifierTypeAndDeletedFalse(
      String identifier, IdentifierType identifierType);

  boolean existsByIdentifierAndIdentifierTypeAndDeletedFalse(
      String identifier, IdentifierType identifierType);
}
