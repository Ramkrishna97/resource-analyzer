package com.example.pageanalyzer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

@Component
public class DocumentExtractor implements ContentExtractor<MultipartFile> {
    @Override
    public ExtractedContent extract(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty file.");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        String text;
        List<ImageAsset> images = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            if (lower.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(file.getBytes())) {
                    StringBuilder out = new StringBuilder();
                    for (int pageNumber = 1; pageNumber <= pdf.getNumberOfPages(); pageNumber++) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        stripper.setStartPage(pageNumber); stripper.setEndPage(pageNumber);
                        out.append(stripper.getText(pdf).trim()).append("\n\n");
                        collectPdfImages(pdf.getPage(pageNumber - 1).getResources(), images, pageNumber, out);
                    }
                    text = out.toString();
                }
            } else if (lower.endsWith(".docx")) {
                try (var doc = new XWPFDocument(in)) {
                    StringBuilder out = new StringBuilder();
                    for (IBodyElement element : doc.getBodyElements()) {
                        if (element instanceof XWPFParagraph paragraph) appendParagraph(paragraph, out, images);
                        else if (element instanceof XWPFTable table) {
                            for (var row : table.getRows()) {
                                out.append(String.join(" | ", row.getTableCells().stream().map(c -> c.getText().trim()).toList())).append('\n');
                                for (var cell : row.getTableCells()) for (var paragraph : cell.getParagraphs())
                                    for (var run : paragraph.getRuns()) for (var picture : run.getEmbeddedPictures())
                                        appendPicture(picture.getPictureData().getData(), picture.getPictureData().getPackagePart().getContentType(), out, images, "Document image");
                            }
                            out.append('\n');
                        }
                    }
                    text = out.toString();
                }
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(in)) {
                    DataFormatter formatter = new DataFormatter(); StringBuilder out = new StringBuilder();
                    for (Sheet sheet : workbook) {
                        out.append("## ").append(sheet.getSheetName()).append("\n\n");
                        Map<Integer, List<Picture>> picturesByRow = new HashMap<>();
                        Drawing<?> drawing = sheet.getDrawingPatriarch();
                        if (drawing != null) for (Shape shape : drawing) if (shape instanceof Picture picture) {
                            int row = picture.getClientAnchor() == null ? 0 : picture.getClientAnchor().getRow1();
                            picturesByRow.computeIfAbsent(row, key -> new ArrayList<>()).add(picture);
                        }
                        picturesByRow.values().forEach(pictures -> pictures.sort((a,b) -> Integer.compare(a.getClientAnchor().getCol1(), b.getClientAnchor().getCol1())));
                        for (Row row : sheet) {
                            List<String> cells = new ArrayList<>();
                            for (Cell cell : row) cells.add(formatter.formatCellValue(cell));
                            out.append(String.join(" | ", cells)).append("\n\n");
                            for (Picture picture : picturesByRow.getOrDefault(row.getRowNum(), List.of()))
                                appendPicture(picture.getPictureData().getData(), picture.getPictureData().getMimeType(), out, images, "Spreadsheet image");
                        }
                        for (var entry : picturesByRow.entrySet()) if (sheet.getRow(entry.getKey()) == null)
                            for (Picture picture : entry.getValue()) appendPicture(picture.getPictureData().getData(), picture.getPictureData().getMimeType(), out, images, "Spreadsheet image");
                    }
                    text = out.toString();
                }
            } else if (lower.endsWith(".doc")) {
                throw new IllegalArgumentException("Legacy .doc is not supported yet. Save it as .docx and try again.");
            } else throw new IllegalArgumentException("Supported file types: PDF, DOCX, XLSX, XLS.");
        }
        return new ExtractedContent(name, text, List.of(), images, "File: " + name);
    }
    private void appendParagraph(XWPFParagraph paragraph, StringBuilder out, List<ImageAsset> images) {
        StringBuilder segment = new StringBuilder();
        for (var run : paragraph.getRuns()) {
            String runText = run.text();
            if (runText != null && !runText.isBlank()) segment.append(runText);
            for (var picture : run.getEmbeddedPictures()) {
                if (!segment.isEmpty()) { out.append(segment.toString().trim()).append("\n\n"); segment.setLength(0); }
                appendPicture(picture.getPictureData().getData(), picture.getPictureData().getPackagePart().getContentType(), out, images, "Document image");
            }
        }
        if (!segment.isEmpty()) out.append(segment.toString().trim()).append("\n\n");
    }
    private void appendPicture(byte[] bytes, String mime, StringBuilder out, List<ImageAsset> images, String label) {
        int index = images.size();
        images.add(embeddedImage(bytes, mime, label + " " + (index + 1)));
        out.append("[[IMAGE:").append(index).append("]]\n\n");
    }
    private void collectPdfImages(PDResources resources, List<ImageAsset> images, int pageNumber, StringBuilder out) throws Exception {
        if (resources == null) return;
        for (var name : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDImageXObject image) {
                try (var bytes = new ByteArrayOutputStream()) {
                    javax.imageio.ImageIO.write(image.getImage(), "png", bytes);
                    appendPicture(bytes.toByteArray(), "image/png", out, images, "Page " + pageNumber + " image");
                }
            } else if (object instanceof PDFormXObject form) collectPdfImages(form.getResources(), images, pageNumber, out);
        }
    }
    private ImageAsset embeddedImage(byte[] bytes, String mime, String label) {
        String type = mime == null || !mime.startsWith("image/") ? "image/png" : mime;
        return new ImageAsset("data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes), label, "", "");
    }
}
