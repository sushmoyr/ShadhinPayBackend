package com.shadhinpay.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@DataJpaTest
class AuditableEntityTest {

  @Autowired private TestEntityManager em;

  @Test
  void persistingAuditable_populatesCreatedAt() {
    SampleEntity e = new SampleEntity();
    e.setName("hello");

    SampleEntity saved = em.persistFlushFind(e);

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(saved.isDeleted()).isFalse();
  }

  @Test
  void updatingAuditable_advancesUpdatedAt() throws InterruptedException {
    SampleEntity e = new SampleEntity();
    e.setName("first");
    SampleEntity saved = em.persistFlushFind(e);
    var initialUpdatedAt = saved.getUpdatedAt();

    Thread.sleep(10);
    saved.setName("second");
    em.persistAndFlush(saved);
    em.clear();
    SampleEntity reloaded = em.find(SampleEntity.class, saved.getId());

    assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);
    assertThat(reloaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
  }

  @SpringBootApplication
  @EntityScan(basePackageClasses = SampleEntity.class)
  @EnableJpaRepositories(basePackageClasses = SampleEntity.class)
  @Configuration
  static class TestApp {}

  @Entity
  @Table(name = "sample_auditable_entity")
  @Getter
  @Setter
  @NoArgsConstructor
  static class SampleEntity extends AuditableAndSoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
  }
}
