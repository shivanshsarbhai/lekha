package com.lekha.statements;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds small one-page PDFs from lines of text, so parsers can be tested end to end. */
final class TestPdfs {

	private TestPdfs() {
	}

	static byte[] withLines(List<String> lines) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
				content.setLeading(10);
				content.newLineAtOffset(20, 760);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}

}
