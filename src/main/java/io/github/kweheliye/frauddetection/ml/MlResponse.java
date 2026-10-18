package io.github.kweheliye.frauddetection.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The sidecar's answer: probability of fraud between 0 and 1. */
public record MlResponse(double score, @JsonProperty("model_version") String modelVersion) {
}
