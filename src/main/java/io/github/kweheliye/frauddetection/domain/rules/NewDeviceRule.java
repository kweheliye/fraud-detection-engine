package io.github.kweheliye.frauddetection.domain.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

@Component
@ConditionalOnProperty(name = "fraud.rules.new-device.enabled", havingValue = "true", matchIfMissing = true)
public class NewDeviceRule implements Rule {

    private final String name;
    private final int weight;

    public NewDeviceRule(@Value("${fraud.rules.new-device.name}") String name,
                        @Value("${fraud.rules.new-device.weight}") int weight) {
        this.name = name;
        this.weight = weight;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public boolean evaluate(Transaction txn, CustomerProfile profile) {
        return !profile.devices().contains(txn.deviceId());
    }
}