package io.github.kweheliye.frauddetection;

import org.springframework.boot.SpringApplication;

public class TestFraudDetectionEngineApplication {

	public static void main(String[] args) {
		SpringApplication.from(FraudDetectionEngineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
