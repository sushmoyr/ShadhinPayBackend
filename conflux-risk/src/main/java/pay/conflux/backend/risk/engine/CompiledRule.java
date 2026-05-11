package pay.conflux.backend.risk.engine;

import org.springframework.expression.Expression;
import pay.conflux.backend.risk.entity.RiskRule;

public record CompiledRule(RiskRule rule, Expression expression) {}
