package com.lekha.analytics;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {

	private final AnalyticsService service;

	AnalyticsController(AnalyticsService service) {
		this.service = service;
	}

	/** POST because a query is a structured object that doesn't fit cleanly into a URL. It only reads. */
	@PostMapping("/query")
	QueryResult query(@RequestBody AnalyticsQueryRequest request) {
		return service.query(request);
	}

}
