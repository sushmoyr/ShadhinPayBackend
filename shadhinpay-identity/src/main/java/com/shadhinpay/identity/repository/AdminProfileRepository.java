package com.shadhinpay.identity.repository;

import com.shadhinpay.identity.entity.AdminProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, UUID> {

  Optional<AdminProfile> findByUserId(UUID userId);
}
