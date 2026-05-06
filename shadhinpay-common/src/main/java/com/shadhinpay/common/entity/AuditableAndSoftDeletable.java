package com.shadhinpay.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class AuditableAndSoftDeletable extends Auditable {

  @Column(name = "deleted", nullable = false, columnDefinition = "boolean default false")
  private boolean deleted = false;
}
