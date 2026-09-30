package com.lekha.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SystemInfoController.class)
@TestPropertySource(properties = "lekha.version=test")
class SystemInfoControllerTest {

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired
	MockMvcTester mvc;

	@Test
	void returnsSystemInfo() {
		assertThat(mvc.get().uri("/api/v1/system/info"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"name": "Lekha", "version": "test", "serverTime": "2026-01-01T00:00:00Z"}
						""");
	}
}
