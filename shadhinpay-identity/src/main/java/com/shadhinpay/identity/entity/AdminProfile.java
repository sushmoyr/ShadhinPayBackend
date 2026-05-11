package com.shadhinpay.identity.entity;

import com.shadhinpay.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "admin_profiles")
public class AdminProfile extends Auditable {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "department", nullable = false)
  private String department;

  @Column(name = "employee_id", nullable = false, unique = true)
  private String employeeId;
}
