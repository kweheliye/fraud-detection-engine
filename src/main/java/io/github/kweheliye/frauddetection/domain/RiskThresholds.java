package io.github.kweheliye.frauddetection.domain;

public record RiskThresholds(int mediumFrom, int highFrom) {

    public RiskLevel levelFor(int score) {
        if (score >= highFrom) {
            return RiskLevel.HIGH;
        }
        if (score >= mediumFrom) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
