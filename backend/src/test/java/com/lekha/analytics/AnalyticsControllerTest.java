package com.lekha.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	AnalyticsService service;

	@Test
	void postPassesTheQueryThroughAndReturnsTheTable() {
		AnalyticsQueryRequest request = new AnalyticsQueryRequest(List.of("spending"), List.of("day_type"),
				Map.of("account", List.of("11111111-1111-1111-1111-111111111111")), LocalDate.of(2026, 9, 1),
				LocalDate.of(2026, 9, 30));
		given(service.query(request)).willReturn(new QueryResult(List.of("day_type", "spending"),
				List.of(List.of("WEEKDAY", new BigDecimal("1200.50")), List.of("WEEKEND", new BigDecimal("301.00"))),
				2));

		var response = assertThat(post("""
				{
				  "metrics": ["spending"],
				  "dimensions": ["day_type"],
				  "filters": { "account": ["11111111-1111-1111-1111-111111111111"] },
				  "from": "2026-09-01",
				  "to": "2026-09-30"
				}
				""")).hasStatusOk().bodyJson();

		response.extractingPath("$.columns").isEqualTo(List.of("day_type", "spending"));
		response.extractingPath("$.rows[1][0]").isEqualTo("WEEKEND");
		response.extractingPath("$.rows[1][1]").isEqualTo(301.0);
		response.extractingPath("$.unclassifiedCount").isEqualTo(2);
	}

	@Test
	void anInvalidQueryIs400WithTheReason() {
		given(service.query(any())).willThrow(new InvalidQueryException("Unknown metric \"profit\""));

		assertThat(post("""
				{ "metrics": ["profit"], "from": "2026-09-01", "to": "2026-09-30" }
				""")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Unknown metric \"profit\"");
	}

	@Test
	void aMalformedDateIs400() {
		assertThat(post("""
				{ "metrics": ["spending"], "from": "September", "to": "2026-09-30" }
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private MockMvcTester.MockMvcRequestBuilder post(String body) {
		return mvc.post().uri("/api/v1/analytics/query").contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
