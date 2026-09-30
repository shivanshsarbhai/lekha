package com.lekha.system;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemInfoController {

	private final String version;
	private final Clock clock;

	SystemInfoController(@Value("${lekha.version}") String version, Clock clock) {
		this.version = version;
		this.clock = clock;
	}

	@GetMapping("/info")
	SystemInfo info() {
		return new SystemInfo("Lekha", version, Instant.now(clock));
	}
}
