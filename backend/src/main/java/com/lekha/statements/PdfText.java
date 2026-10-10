package com.lekha.statements;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;

/** Turns a PDF into its lines of text, for parsers of PDF statements. */
final class PdfText {

	private PdfText() {
	}

	/**
	 * Opens the PDF in memory, unlocking it with {@code password} if it has one. A PDF that only restricts printing or
	 * copying opens without a password.
	 */
	static List<String> lines(byte[] pdf, @Nullable String password) {
		boolean hasPassword = password != null && !password.isEmpty();
		try (PDDocument document = Loader.loadPDF(pdf, hasPassword ? password : "")) {
			return new PDFTextStripper().getText(document).lines().toList();
		}
		catch (InvalidPasswordException ex) {
			throw new StatementPasswordException(hasPassword);
		}
		catch (IOException ex) {
			throw new StatementFormatException("Could not read the file as a PDF", ex);
		}
	}

}
