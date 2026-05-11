package pay.conflux.backend.risk.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.entity.RuleAction;

class RiskRuleMapperTest {

  private final RiskRuleMapper mapper = new RiskRuleMapper();

  @Test
  void shouldMapToDto() {
    RiskRule rule = new RiskRule();
    rule.setId(UUID.randomUUID());
    rule.setName("test");
    rule.setExpression("true");
    rule.setScoreWeight(10);
    rule.setAction(RuleAction.BLOCK);
    rule.setActive(true);

    RiskRuleDto dto = mapper.toDto(rule);

    assertNotNull(dto);
    assertEquals(rule.getId(), dto.id());
    assertEquals(rule.getName(), dto.name());
    assertEquals(rule.getExpression(), dto.expression());
    assertEquals(rule.getScoreWeight(), dto.scoreWeight());
    assertEquals(rule.getAction(), dto.action());
    assertTrue(dto.active());
  }

  @Test
  void shouldReturnNullWhenNull() {
    assertNull(mapper.toDto(null));
  }
}
