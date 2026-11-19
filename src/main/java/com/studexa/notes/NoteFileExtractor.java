package com.studexa.notes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.studexa.common.ApiException;

final class NoteFileExtractor {

    private static final int MAX_TEXT = 200_000;
    private static final int MAX_PDF_PAGES = 200;
    private static final long MAX_PDF_SCRATCH_BYTES = 32L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 256;
    private static final int MAX_UNCOMPRESSED_BYTES = 25 * 1024 * 1024;

    private NoteFileExtractor() {}

    record ExtractedText(String text, boolean truncated) {}

    static ExtractedText extract(String filename, byte[] bytes) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            return switch (extension) {
                case "pdf" -> pdf(bytes);
                case "docx" -> word(bytes);
                case "txt", "md" -> {
                    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                    if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c))) {
                        throw new IOException("Binary content in text file");
                    }
                    yield limit(text, false);
                }
                default -> throw ApiException.badRequest("UNSUPPORTED_FILE", "Choose a PDF, DOCX, TXT, or Markdown file.");
            };
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ApiException apiException) throw apiException;
            throw ApiException.badRequest("INVALID_FILE", "This file could not be read. Check that it opens and is not password protected.");
        }
    }

    private static ExtractedText pdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IOException("Missing PDF header");
        }
        try (var input = new RandomAccessReadBuffer(bytes);
                var document = Loader.loadPDF(input, MemoryUsageSetting.setupTempFileOnly(MAX_PDF_SCRATCH_BYTES).streamCache)) {
            var output = new TextWriter();
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(MAX_PDF_PAGES);
            boolean truncated = document.getNumberOfPages() > MAX_PDF_PAGES;
            try {
                stripper.writeText(document, output);
            } catch (TextLimitReached ex) {
                truncated = true;
            }
            return limit(output.text.toString(), truncated);
        }
    }

    private static ExtractedText word(byte[] bytes) throws IOException {
        checkZipSize(bytes);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
                var extractor = new XWPFWordExtractor(document)) {
            if (!"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                    .equals(document.getPackagePart().getContentType())) {
                throw new IOException("Unsupported Word document type");
            }
            extractor.setCloseFilesystem(false);
            return limit(extractor.getText(), false);
        }
    }

    private static void checkZipSize(byte[] bytes) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries = 0;
            int total = 0;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IOException("Too many Word archive entries");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("Word archive exceeds processing limit");
                }
            }
        }
    }

    private static ExtractedText limit(String value, boolean truncated) {
        value = value.replace("\u0000", "").replaceFirst("^\uFEFF", "").strip();
        int end = Math.min(value.length(), MAX_TEXT);
        int safeEnd = end > 0 && Character.isHighSurrogate(value.charAt(end - 1)) ? end - 1 : end;
        return new ExtractedText(value.substring(0, safeEnd).stripTrailing(), truncated || safeEnd < value.length());
    }

    private static final class TextWriter extends Writer {
        private final StringBuilder text = new StringBuilder();

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            int count = Math.min(length, MAX_TEXT - text.length());
            text.append(chars, offset, count);
            if (count < length) throw new TextLimitReached();
        }

        @Override public void flush() {}
        @Override public void close() {}
    }

    private static final class TextLimitReached extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
