package com.lekha.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TimeGrainTest {

	@Test
	void monthsCoverEveryMonthTheRangeTouches() {
		assertThat(TimeGrain.MONTH.buckets(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 9, 2)))
			.containsExactly("2026-07", "2026-08", "2026-09");
	}

	@Test
	void weeksStartOnMondayEvenWhenTheRangeStartsMidWeek() {
		// 2026-09-03 is a Thursday, so its week starts on Monday 2026-08-31.
		assertThat(TimeGrain.WEEK.buckets(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 14)))
			.containsExactly("2026-08-31", "2026-09-07", "2026-09-14");
	}

	@Test
	void quartersAreLabelledLikePostgresDoes() {
		assertThat(TimeGrain.QUARTER.buckets(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 4, 1)))
			.containsExactly("2025-Q4", "2026-Q1", "2026-Q2");
	}

	@Test
	void daysAndYears() {
		assertThat(TimeGrain.DAY.buckets(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 1)))
			.containsExactly("2026-02-27", "2026-02-28", "2026-03-01");
		assertThat(TimeGrain.YEAR.buckets(LocalDate.of(2025, 6, 1), LocalDate.of(2026, 1, 1)))
			.containsExactly("2025", "2026");
	}

}
