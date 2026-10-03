package com.example.docassistant.document;

import com.example.docassistant.common.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads a PDF and returns its text page by page. */
@Component
public class PdfTextExtractor {

    /** Text of one page; {@code number} starts at 1. */
    public record PageText(int number, String text) {
    }

    private final int maxPages;

    PdfTextExtractor(@Value("${app.documents.max-pages:300}") int maxPages) {
        this.maxPages = maxPages;
    }

    public List<PageText> extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                throw new BadRequestException("The PDF has no pages");
            }
            if (pages > maxPages) {
                throw new BadRequestException("The PDF has " + pages + " pages; the limit is " + maxPages);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<PageText> result = new ArrayList<>(pages);
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                result.add(new PageText(page, stripper.getText(document)));
            }
            return result;
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("Password-protected PDFs are not supported");
        } catch (IOException e) {
            throw new BadRequestException("The file is not a readable PDF");
        }
    }
}
