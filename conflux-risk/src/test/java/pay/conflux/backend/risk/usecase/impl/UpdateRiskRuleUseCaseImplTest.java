package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.dto.UpdateRiskRuleRequest;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.entity.RuleAction;
import pay.conflux.backend.risk.events.RiskRuleChangedEvent;
import pay.conflux.backend.risk.mapper.RiskRuleMapper;
import pay.conflux.backend.risk.repository.RiskRuleRepository;

class UpdateRiskRuleUseCaseImplTest {

  private RiskRuleRepository riskRuleRepository;
  private RiskRuleMapper riskRuleMapper;
  private ApplicationEventPublisher eventPublisher;
  private UpdateRiskRuleUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    riskRuleRepository = mock(RiskRuleRepository.class);
    riskRuleMapper = new RiskRuleMapper();
    eventPublisher = mock(ApplicationEventPublisher.class);
    useCase = new UpdateRiskRuleUseCaseImpl(riskRuleRepository, riskRuleMapper, eventPublisher);
  }

  @Test
  void shouldUpdateRuleAndPublishUpdatedEvent() {
    UUID id = UUID.randomUUID();
    UpdateRiskRuleRequest req = new UpdateRiskRuleRequest("expr2", 20, RuleAction.FLAG);
    RiskRule rule = new RiskRule();
    rule.setId(id);
    rule.setName("rule1");

    when(riskRuleRepository.findById(id)).thenReturn(Optional.of(rule));
    when(riskRuleRepository.save(any(RiskRule.class))).thenReturn(rule);

    RiskRuleDto res = useCase.execute(id, req);

    assertNotNull(res);
    assertEquals("expr2", rule.getExpression());
    verify(riskRuleRepository, times(1)).save(rule);
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Object captured = captor.getValue();
    assertInstanceOf(RiskRuleChangedEvent.class, captured);
    assertEquals(RiskRuleChangedEvent.ChangeKind.UPDATED, ((RiskRuleChangedEvent) captured).kind());
  }

  @Test
  void shouldThrowNotFoundException() {
    UUID id = UUID.randomUUID();
    UpdateRiskRuleRequest req = new UpdateRiskRuleRequest("expr2", 20, RuleAction.FLAG);

    when(riskRuleRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> useCase.execute(id, req));
    verifyNoInteractions(eventPublisher);
  }
}
