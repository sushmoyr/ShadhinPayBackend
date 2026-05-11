package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.risk.dto.CreateRiskRuleRequest;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.entity.RuleAction;
import pay.conflux.backend.risk.events.RiskRuleChangedEvent;
import pay.conflux.backend.risk.mapper.RiskRuleMapper;
import pay.conflux.backend.risk.repository.RiskRuleRepository;

class CreateRiskRuleUseCaseImplTest {

  private RiskRuleRepository riskRuleRepository;
  private RiskRuleMapper riskRuleMapper;
  private ApplicationEventPublisher eventPublisher;
  private CreateRiskRuleUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    riskRuleRepository = mock(RiskRuleRepository.class);
    riskRuleMapper = new RiskRuleMapper();
    eventPublisher = mock(ApplicationEventPublisher.class);
    useCase = new CreateRiskRuleUseCaseImpl(riskRuleRepository, riskRuleMapper, eventPublisher);
  }

  @Test
  void shouldCreateRuleAndPublishCreatedEvent() {
    CreateRiskRuleRequest req = new CreateRiskRuleRequest("rule1", "expr", 10, RuleAction.BLOCK);

    when(riskRuleRepository.existsByNameAndDeletedFalse("rule1")).thenReturn(false);
    when(riskRuleRepository.save(any(RiskRule.class)))
        .thenAnswer(
            i -> {
              RiskRule r = i.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });

    RiskRuleDto res = useCase.execute(req);

    assertNotNull(res);
    assertEquals("rule1", res.name());
    verify(riskRuleRepository, times(1)).save(any(RiskRule.class));
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Object captured = captor.getValue();
    assertInstanceOf(RiskRuleChangedEvent.class, captured);
    assertEquals(RiskRuleChangedEvent.ChangeKind.CREATED, ((RiskRuleChangedEvent) captured).kind());
  }

  @Test
  void shouldThrowDuplicateResourceExceptionIfNameExists() {
    CreateRiskRuleRequest req = new CreateRiskRuleRequest("rule1", "expr", 10, RuleAction.BLOCK);

    when(riskRuleRepository.existsByNameAndDeletedFalse("rule1")).thenReturn(true);

    assertThrows(DuplicateResourceException.class, () -> useCase.execute(req));
    verify(riskRuleRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }
}
