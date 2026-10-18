package io.github.kweheliye.frauddetection.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fraud.rules")
public record RulesProperties(@DefaultValue AmountThreshold amountThreshold) {

    public record AmountThreshold(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("10000") BigDecimal threshold,
            @DefaultValue("40") int weight) {
    }
}