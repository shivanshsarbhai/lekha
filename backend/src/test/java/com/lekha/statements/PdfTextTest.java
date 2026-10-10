package com.lekha.statements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class PdfTextTest {

	private static final List<String> LINES = List.of("Made-up statement", "01/09/26 UPI-TEST 450.00");

	@Test
	void readsAnUnlockedPdf() throws IOException {
		assertThat(PdfText.lines(TestPdfs.withLines(LINES), null)).containsSubsequence(LINES);
	}

	@Test
	void anUnlockedPdfIgnoresAPassword() throws IOException {
		assertThat(PdfText.lines(TestPdfs.withLines(LINES), "anything")).containsSubsequence(LINES);
	}

	@Test
	void unlocksAPdfWithItsPassword() throws IOException {
		assertThat(PdfText.lines(TestPdfs.locked(LINES, "TEST0101"), "TEST0101")).containsSubsequence(LINES);
	}

	@Test
	void asksForThePasswordOfALockedPdf() throws IOException {
		byte[] pdf = TestPdfs.locked(LINES, "TEST0101");

		assertThatThrownBy(() -> PdfText.lines(pdf, null)).isInstanceOfSatisfying(StatementPasswordException.class,
				ex -> assertThat(ex.code()).isEqualTo("PASSWORD_REQUIRED"));
		assertThatThrownBy(() -> PdfText.lines(pdf, "")).isInstanceOfSatisfying(StatementPasswordException.class,
				ex -> assertThat(ex.code()).isEqualTo("PASSWORD_REQUIRED"));
	}

	@Test
	void saysWhenThePasswordIsWrongWithoutRepeatingIt() throws IOException {
		byte[] pdf = TestPdfs.locked(LINES, "TEST0101");

		assertThatThrownBy(() -> PdfText.lines(pdf, "WRONG999"))
			.isInstanceOfSatisfying(StatementPasswordException.class,
					ex -> assertThat(ex.code()).isEqualTo("WRONG_PASSWORD"))
			.hasMessage("That password didn't open the PDF. Check it and try again.")
			.message()
			.doesNotContain("WRONG999");
	}

	@Test
	void rejectsAFileThatIsNotAPdf() {
		assertThatThrownBy(() -> PdfText.lines("not a pdf".getBytes(), null))
			.isInstanceOf(StatementFormatException.class)
			.hasMessage("Could not read the file as a PDF");
	}

}
