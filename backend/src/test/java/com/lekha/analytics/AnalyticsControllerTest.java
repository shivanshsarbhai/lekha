package com.lekha.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

	@Test
	void piecesPassesTheRequestThroughAndReturnsTheList() {
		UUID pieceId = UUID.fromString("33333333-3333-3333-3333-333333333333");
		UUID transactionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
		UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		PiecesRequest request = new PiecesRequest("spending", Map.of("category", List.of("none")),
				LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
		given(service.pieces(request)).willReturn(new PieceList(new BigDecimal("50.00"), 1, false,
				List.of(new PieceList.Piece(pieceId, transactionId, LocalDate.of(2026, 9, 10),
						LocalDate.of(2026, 9, 10), "CHAI", new BigDecimal("-50.00"), "UPI", accountId, "HDFC Salary",
						"EXPENSE", null, null, null, null, new BigDecimal("50.00"), false))));

		var response = assertThat(mvc.post()
			.uri("/api/v1/analytics/pieces")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "metric": "spending",
					  "filters": { "category": ["none"] },
					  "from": "2026-09-01",
					  "to": "2026-09-30"
					}
					""")).hasStatusOk().bodyJson();

		response.extractingPath("$.total").isEqualTo(50.0);
		response.extractingPath("$.count").isEqualTo(1);
		response.extractingPath("$.truncated").isEqualTo(false);
		response.extractingPath("$.pieces[0].transactionId").isEqualTo(transactionId.toString());
		response.extractingPath("$.pieces[0].contribution").isEqualTo(50.0);
		response.extractingPath("$.pieces[0].reportedOn").isEqualTo("2026-09-10");
	}

	@Test
	void piecesOfARateIs400WithTheReason() {
		given(service.pieces(any())).willThrow(new InvalidQueryException("\"savings_rate\" isn't a sum of money"));

		assertThat(mvc.post()
			.uri("/api/v1/analytics/pieces")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "metric": "savings_rate", "from": "2026-09-01", "to": "2026-09-30" }
					""")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("\"savings_rate\" isn't a sum of money");
	}

	private MockMvcTester.MockMvcRequestBuilder post(String body) {
		return mvc.post().uri("/api/v1/analytics/query").contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
