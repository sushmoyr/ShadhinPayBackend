package com.shadhinpay.risk.engine;

import com.shadhinpay.risk.entity.RiskRule;
import org.springframework.expression.Expression;

public record CompiledRule(RiskRule rule, Expression expression) {}
