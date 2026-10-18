package io.github.kweheliye.frauddetection.domain;

import java.util.List;

public record Decision(int score, RiskLevel riskLevel, List<String> triggeredRules) {
}
