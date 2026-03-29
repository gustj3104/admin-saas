package com.adminsaas.accounting.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReceiptOcrService {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:krw\\s*|₩\\s*|￦\\s*)?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_PATTERN = Pattern.compile("(?:item|description|menu|product|품목|상품명|메뉴|내역)\\s*[:：]?\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(?:qty|quantity|ea|수량|개수)\\s*[:：xX]?\\s*(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT_PRICE_PATTERN = Pattern.compile("(?:unit\\s*price|price|단가)\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile("(.+?)\\s+(\\d+(?:[.,]\\d+)?)\\s*[xX]\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,6})");
    private static final int PDF_RENDER_DPI = 300;
    private static final int PDF_MAX_PAGES = 3;

    private final FileStorageService fileStorageService;
    private final String ocrCommand;
    private final String ocrLanguage;
    private final long timeoutSeconds;

    public ReceiptOcrService(
            FileStorageService fileStorageService,
            @Value("${app.ocr.command}") String ocrCommand,
            @Value("${app.ocr.language}") String ocrLanguage,
            @Value("${app.ocr.timeout-seconds}") long timeoutSeconds
    ) {
        this.fileStorageService = fileStorageService;
        this.ocrCommand = ocrCommand;
        this.ocrLanguage = ocrLanguage;
        this.timeoutSeconds = timeoutSeconds;
    }

    public ReceiptOcrResult analyze(Long projectId, MultipartFile file) throws IOException {
        FileStorageService.StoredFile storedFile = fileStorageService.store(projectId, "receipt", file);
        Path storedPath = Path.of(storedFile.path());

        if (!isSupportedFile(storedPath)) {
            return unsupportedResult(storedFile, "Only image files and PDFs are supported for OCR.");
        }

        Optional<String> rawText = extractText(storedPath);
        if (rawText.isEmpty()) {
            return unavailableResult(storedFile, "Tesseract OCR is not available. Install it and set OCR_COMMAND if needed.");
        }

        String extractedText = rawText.get().trim();
        ParsedReceiptFields fields = parseFields(extractedText);

        return new ReceiptOcrResult(
                storedFile.originalFilename(),
                storedFile.storedFilename(),
                storedFile.size(),
                storedFile.path(),
                true,
                "COMPLETED",
                extractedText,
                estimateConfidence(fields, extractedText),
                fields,
                buildWarnings(fields)
        );
    }

    private Optional<String> extractText(Path storedPath) throws IOException {
        if (isPdf(storedPath)) {
            return extractTextFromPdf(storedPath);
        }
        return runTesseract(storedPath);
    }

    private Optional<String> extractTextFromPdf(Path pdfPath) throws IOException {
        List<String> pageTexts = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), PDF_MAX_PAGES);

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI, ImageType.RGB);
                Path tempImage = Files.createTempFile("receipt-pdf-page-" + pageIndex + "-", ".png");

                try {
                    ImageIO.write(image, "png", tempImage.toFile());
                    Optional<String> pageText = runTesseract(tempImage);
                    pageText.filter(text -> !text.isBlank()).ifPresent(pageTexts::add);
                } finally {
                    Files.deleteIfExists(tempImage);
                }
            }
        }

        if (pageTexts.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(String.join(System.lineSeparator() + System.lineSeparator(), pageTexts));
    }

    private Optional<String> runTesseract(Path inputPath) {
        Path outputBase = null;
        try {
            outputBase = Files.createTempFile("receipt-ocr-", "");
            Files.deleteIfExists(outputBase);

            Process process = new ProcessBuilder(
                    ocrCommand,
                    inputPath.toString(),
                    outputBase.toString(),
                    "-l",
                    ocrLanguage
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }

            if (process.exitValue() != 0) {
                consumeProcessOutput(process);
                return Optional.empty();
            }

            Path textOutput = Path.of(outputBase + ".txt");
            if (!Files.exists(textOutput)) {
                return Optional.empty();
            }

            return Optional.of(Files.readString(textOutput, StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        } finally {
            if (outputBase != null) {
                try {
                    Files.deleteIfExists(outputBase);
                    Files.deleteIfExists(Path.of(outputBase + ".txt"));
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void consumeProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // Drain output so the process cannot block on a full buffer.
            }
        }
    }

    private boolean isSupportedFile(Path path) {
        return isImage(path) || isPdf(path);
    }

    private boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".bmp")
                || name.endsWith(".tif")
                || name.endsWith(".tiff");
    }

    private boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private ParsedReceiptFields parseFields(String rawText) {
        List<String> lines = rawText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        List<ReceiptLineItem> lineItems = extractLineItems(lines);
        ReceiptLineItem primaryLineItem = lineItems.isEmpty() ? null : lineItems.get(0);

        LocalDate paymentDate = extractDate(rawText).orElse(null);
        String vendor = extractVendor(lines).orElse(null);
        String itemName = Optional.ofNullable(primaryLineItem)
                .map(ReceiptLineItem::itemName)
                .or(() -> extractItemName(lines))
                .orElse(null);
        BigDecimal quantity = Optional.ofNullable(primaryLineItem)
                .map(ReceiptLineItem::quantity)
                .or(() -> extractQuantity(lines))
                .orElse(null);
        BigDecimal unitPrice = Optional.ofNullable(primaryLineItem)
                .map(ReceiptLineItem::unitPrice)
                .or(() -> extractUnitPrice(lines))
                .orElse(null);
        BigDecimal amount = extractAmount(lines).orElseGet(() ->
                Optional.ofNullable(primaryLineItem).map(ReceiptLineItem::amount).orElse(null)
        );
        String paymentMethod = extractPaymentMethod(rawText).orElse(null);
        String category = inferCategory(rawText, itemName);
        String subcategory = inferSubcategory(category, itemName);

        if (quantity != null && unitPrice != null && amount == null) {
            amount = quantity.multiply(unitPrice);
        }

        return new ParsedReceiptFields(paymentDate, vendor, itemName, quantity, unitPrice, amount, paymentMethod, category, subcategory, lineItems);
    }

    private Optional<LocalDate> extractDate(String rawText) {
        Matcher matcher = DATE_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> extractVendor(List<String> lines) {
        return lines.stream()
                .filter(line -> line.length() >= 2)
                .filter(line -> !containsAny(line.toLowerCase(Locale.ROOT), "receipt", "approved", "total", "vat", "business", "영수증", "승인", "합계", "사업자"))
                .findFirst();
    }

    private Optional<String> extractItemName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = ITEM_PATTERN.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group(1).trim());
            }
        }

        return lines.stream()
                .filter(line -> line.length() >= 2)
                .filter(line -> !containsAny(line.toLowerCase(Locale.ROOT), "receipt", "total", "vat", "card", "cash", "영수증", "합계", "현금", "카드"))
                .skip(1)
                .findFirst();
    }

    private List<ReceiptLineItem> extractLineItems(List<String> lines) {
        List<ReceiptLineItem> lineItems = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = LINE_ITEM_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }

            Optional<BigDecimal> quantity = parseDecimal(matcher.group(2));
            Optional<BigDecimal> unitPrice = parseDecimal(matcher.group(3));
            if (quantity.isEmpty() || unitPrice.isEmpty()) {
                continue;
            }

            String itemName = matcher.group(1).trim();
            if (itemName.length() < 2 || containsAny(itemName.toLowerCase(Locale.ROOT), "total", "합계", "subtotal")) {
                continue;
            }

            lineItems.add(new ReceiptLineItem(
                    itemName,
                    quantity.get(),
                    unitPrice.get(),
                    quantity.get().multiply(unitPrice.get())
            ));
        }

        return lineItems;
    }

    private Optional<BigDecimal> extractQuantity(List<String> lines) {
        for (String line : lines) {
            Matcher explicitMatcher = QUANTITY_PATTERN.matcher(line);
            if (explicitMatcher.find()) {
                return parseDecimal(explicitMatcher.group(1));
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> extractUnitPrice(List<String> lines) {
        for (String line : lines) {
            Matcher explicitMatcher = UNIT_PRICE_PATTERN.matcher(line);
            if (explicitMatcher.find()) {
                return parseDecimal(explicitMatcher.group(1));
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> extractAmount(List<String> lines) {
        List<BigDecimal> candidates = new ArrayList<>();

        for (String line : lines) {
            if (containsAny(line.toLowerCase(Locale.ROOT), "total", "amount", "paid", "합계", "총액", "결제")) {
                candidates.addAll(findAmounts(line));
            }
        }

        if (candidates.isEmpty()) {
            for (String line : lines) {
                candidates.addAll(findAmounts(line));
            }
        }

        return candidates.stream().max(Comparator.naturalOrder());
    }

    private List<BigDecimal> findAmounts(String line) {
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(line);
        while (matcher.find()) {
            parseDecimal(matcher.group(1)).ifPresent(amounts::add);
        }
        return amounts;
    }

    private Optional<BigDecimal> parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(value.replace(",", "")));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> extractPaymentMethod(String rawText) {
        String normalized = rawText.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "card", "visa", "master", "카드")) {
            return Optional.of("card");
        }
        if (containsAny(normalized, "cash", "현금")) {
            return Optional.of("cash");
        }
        if (containsAny(normalized, "transfer", "bank", "계좌")) {
            return Optional.of("bank");
        }
        return Optional.empty();
    }

    private String inferCategory(String rawText, String itemName) {
        String normalized = (rawText + " " + Optional.ofNullable(itemName).orElse("")).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "train", "bus", "taxi", "flight", "hotel", "travel", "기차", "버스", "택시", "항공", "숙박")) {
            return "travel";
        }
        if (containsAny(normalized, "laptop", "monitor", "printer", "equipment", "pc", "노트북", "모니터", "프린터", "장비")) {
            return "equipment";
        }
        if (containsAny(normalized, "salary", "wage", "consulting", "인건", "급여", "자문")) {
            return "personnel";
        }
        if (containsAny(normalized, "indirect", "overhead", "간접", "관리비")) {
            return "indirect";
        }
        return "material";
    }

    private String inferSubcategory(String category, String itemName) {
        if (category == null) {
            return null;
        }

        String normalizedItem = Optional.ofNullable(itemName).orElse("").toLowerCase(Locale.ROOT);
        return switch (category) {
            case "travel" -> "domestic";
            case "equipment" -> "asset";
            case "personnel" -> containsAny(normalizedItem, "consult", "자문") ? "consultant" : "salaries";
            case "indirect" -> "operations";
            default -> "supplies";
        };
    }

    private double estimateConfidence(ParsedReceiptFields fields, String rawText) {
        int score = 0;
        if (fields.paymentDate() != null) score++;
        if (fields.vendor() != null) score++;
        if (fields.itemName() != null) score++;
        if (fields.quantity() != null) score++;
        if (fields.unitPrice() != null) score++;
        if (fields.amount() != null) score++;
        if (fields.paymentMethod() != null) score++;
        if (!fields.lineItems().isEmpty()) score++;
        if (!rawText.isBlank()) score++;
        return Math.min(0.98d, 0.2d + (score * 0.085d));
    }

    private List<String> buildWarnings(ParsedReceiptFields fields) {
        List<String> warnings = new ArrayList<>();
        if (fields.vendor() == null) {
            warnings.add("Could not detect the vendor automatically.");
        }
        if (fields.itemName() == null) {
            warnings.add("Could not detect the item name automatically.");
        }
        if (fields.quantity() == null && fields.lineItems().isEmpty()) {
            warnings.add("Could not detect the quantity automatically.");
        }
        if (fields.unitPrice() == null && fields.lineItems().isEmpty()) {
            warnings.add("Could not detect the unit price automatically.");
        }
        if (fields.amount() == null) {
            warnings.add("Could not detect the total amount automatically.");
        }
        if (fields.paymentDate() == null) {
            warnings.add("Could not detect the payment date automatically.");
        }
        return warnings;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private ReceiptOcrResult unsupportedResult(FileStorageService.StoredFile storedFile, String warning) {
        return new ReceiptOcrResult(
                storedFile.originalFilename(),
                storedFile.storedFilename(),
                storedFile.size(),
                storedFile.path(),
                false,
                "UNSUPPORTED",
                null,
                null,
                new ParsedReceiptFields(null, null, null, null, null, null, null, null, null, List.of()),
                List.of(warning)
        );
    }

    private ReceiptOcrResult unavailableResult(FileStorageService.StoredFile storedFile, String warning) {
        return new ReceiptOcrResult(
                storedFile.originalFilename(),
                storedFile.storedFilename(),
                storedFile.size(),
                storedFile.path(),
                false,
                "UNAVAILABLE",
                null,
                null,
                new ParsedReceiptFields(null, null, null, null, null, null, null, null, null, List.of()),
                List.of(warning)
        );
    }

    public record ReceiptOcrResult(
            String originalFilename,
            String storedFilename,
            long size,
            String path,
            boolean ocrAvailable,
            String status,
            String rawText,
            Double confidence,
            ParsedReceiptFields fields,
            List<String> warnings
    ) {
    }

    public record ParsedReceiptFields(
            LocalDate paymentDate,
            String vendor,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            String paymentMethod,
            String category,
            String subcategory,
            List<ReceiptLineItem> lineItems
    ) {
    }

    public record ReceiptLineItem(
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {
    }
}
