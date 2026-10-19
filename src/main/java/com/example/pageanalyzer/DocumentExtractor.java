package com.example.pageanalyzer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentExtractor implements ContentExtractor<MultipartFile> {
    @Override
    public ExtractedContent extract(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty file.");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        String text;
        try (InputStream in = file.getInputStream()) {
            if (lower.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(file.getBytes())) { text = new PDFTextStripper().getText(pdf); }
            } else if (lower.endsWith(".docx")) {
                try (var doc = new XWPFDocument(in)) {
                    StringBuilder out = new StringBuilder();
                    for (var p : doc.getParagraphs()) if (!p.getText().isBlank()) out.append(p.getText()).append('\n');
                    for (var table : doc.getTables()) for (var row : table.getRows())
                        out.append(String.join(" | ", row.getTableCells().stream().map(c -> c.getText().trim()).toList())).append('\n');
                    text = out.toString();
                }
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(in)) {
                    DataFormatter formatter = new DataFormatter(); StringBuilder out = new StringBuilder();
                    for (Sheet sheet : workbook) {
                        out.append("## ").append(sheet.getSheetName()).append('\n');
                        for (Row row : sheet) {
                            List<String> cells = new ArrayList<>();
                            for (Cell cell : row) cells.add(formatter.formatCellValue(cell));
                            out.append(String.join(" | ", cells)).append('\n');
                        }
                    }
                    text = out.toString();
                }
            } else if (lower.endsWith(".doc")) {
                throw new IllegalArgumentException("Legacy .doc is not supported yet. Save it as .docx and try again.");
            } else throw new IllegalArgumentException("Supported file types: PDF, DOCX, XLSX, XLS.");
        }
        return new ExtractedContent(name, text, List.of(), "File: " + name);
    }
}
