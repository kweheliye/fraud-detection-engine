package io.github.kweheliye.frauddetection.streams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

/** Creates the three topics on startup if they do not exist yet. */
@Configuration
public class TopicsConfig {

    @Bean
    public NewTopics topics(@Value("${fraud.topics.raw}") String raw,
                            @Value("${fraud.topics.scored}") String scored,
                            @Value("${fraud.topics.flagged}") String flagged) {
        return new NewTopics(
                TopicBuilder.name(raw).build(),
                TopicBuilder.name(scored).build(),
                TopicBuilder.name(flagged).build());
    }
}