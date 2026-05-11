package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.events.RiskRuleChangedEvent;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class DisableRiskRuleUseCaseImplTest {

  @Test
  void shouldDisableRuleAndPublishEvent() {
    RiskRuleRepository riskRuleRepository = mock(RiskRuleRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    DisableRiskRuleUseCaseImpl useCase =
        new DisableRiskRuleUseCaseImpl(riskRuleRepository, eventPublisher);

    UUID id = UUID.randomUUID();
    RiskRule rule = new RiskRule();
    rule.setId(id);
    rule.setActive(true);
    rule.setDeleted(false);

    when(riskRuleRepository.findById(id)).thenReturn(Optional.of(rule));

    useCase.execute(id);

    assertFalse(rule.isActive());
    assertTrue(rule.isDeleted());
    verify(riskRuleRepository, times(1)).save(rule);
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Object captured = captor.getValue();
    assertInstanceOf(RiskRuleChangedEvent.class, captured);
    RiskRuleChangedEvent event = (RiskRuleChangedEvent) captured;
    assertEquals(RiskRuleChangedEvent.ChangeKind.DISABLED, event.kind());
    assertEquals(id, event.ruleId());
  }
}
