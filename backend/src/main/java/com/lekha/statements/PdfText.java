package com.lekha.statements;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/** Turns a PDF into its lines of text, for parsers of PDF statements. */
final class PdfText {

	private PdfText() {
	}

	static List<String> lines(InputStream file) {
		try (PDDocument document = Loader.loadPDF(file.readAllBytes())) {
			return new PDFTextStripper().getText(document).lines().toList();
		}
		catch (InvalidPasswordException ex) {
			throw new StatementFormatException("The PDF is password-protected. Download it again without a password.",
					ex);
		}
		catch (IOException ex) {
			throw new StatementFormatException("Could not read the file as a PDF", ex);
		}
	}

}
