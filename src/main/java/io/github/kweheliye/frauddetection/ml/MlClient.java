package io.github.kweheliye.frauddetection.ml;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the ML sidecar. It never throws: if the sidecar is slow or down the answer is "no score",
 * the other rules still decide, and calls pause briefly so a dead sidecar cannot slow the stream.
 */
@Component
@ConditionalOnProperty(name = "fraud.rules.ml.enabled", havingValue = "true")
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(10);

    private final RestClient http;
    private volatile Instant pausedUntil = Instant.MIN;

    public MlClient(@Value("${fraud.rules.ml.url}") String url,
                    @Value("${fraud.rules.ml.timeout}") Duration timeout) {
        JdkClientHttpRequestFactory requests =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        requests.setReadTimeout(timeout);
        this.http = RestClient.builder().baseUrl(url).requestFactory(requests).build();
    }

    /** Fraud probability 0..1, or empty when the sidecar cannot answer. */
    public OptionalDouble score(MlRequest request) {
        if (Instant.now().isBefore(pausedUntil)) {
            return OptionalDouble.empty();
        }
        try {
            MlResponse response = http.post().uri("/score").body(request).retrieve().body(MlResponse.class);
            return response == null ? OptionalDouble.empty() : OptionalDouble.of(response.score());
        } catch (RuntimeException e) {
            pausedUntil = Instant.now().plus(PAUSE_AFTER_FAILURE);
            log.warn("ML sidecar unavailable ({}). Skipping ML scoring for {}s.", e.getMessage(),
                    PAUSE_AFTER_FAILURE.toSeconds());
            return OptionalDouble.empty();
        }
    }
}
