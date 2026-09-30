package com.lekha.system;

import java.time.Instant;

public record SystemInfo(String name, String version, Instant serverTime) {
}
