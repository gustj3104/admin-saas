package com.adminsaas.accounting.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReceiptOcrService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrService.class);
    private static final Path WINDOWS_TESSERACT_PATH = Path.of("C:\\Program Files\\Tesseract-OCR\\tesseract.exe");

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})");
    private static final Pattern KOREAN_DATE_PATTERN = Pattern.compile("(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern COMPACT_DATE_PATTERN = Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:krw\\s*|￦\\s*|원\\s*)?([0-9oO]{1,3}(?:,[0-9oO]{3})+|[0-9oO]{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_PATTERN = Pattern.compile("(?:item|description|menu|product|내역|품명|메뉴)\\s*[:\\s-]*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(?:qty|quantity|ea|수량|개수)\\s*[:xX*]?\\s*(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT_PRICE_PATTERN = Pattern.compile("(?:unit\\s*price|price|단가)\\s*[:\\s-]*\\s*([0-9oO]{1,3}(?:,[0-9oO]{3})+|[0-9oO]{1,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile("(.+?)\\s+(\\d+(?:[.,]\\d+)?)\\s*[xX*]\\s*([0-9oO]{1,3}(?:,[0-9oO]{3})+|[0-9oO]{1,6})");
    private static final int PDF_RENDER_DPI = 300;
    private static final int PDF_MAX_PAGES = 3;
    private static final Set<String> UNIT_TOKENS = Set.of(
            "ea", "pcs", "pc", "box", "set", "kg", "g", "ml", "l", "m", "cm", "mm",
            "개", "박스", "세트"
    );

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
        this.ocrCommand = resolveOcrCommand(ocrCommand);
        this.ocrLanguage = ocrLanguage;
        this.timeoutSeconds = timeoutSeconds;
    }

    public ReceiptOcrResult analyze(Long projectId, MultipartFile file) throws IOException {
        FileStorageService.StoredFile storedFile = fileStorageService.store(projectId, "receipt", file);
        Path storedPath = Path.of(storedFile.path());

        if (!isSupportedFile(storedPath)) {
            return unsupportedResult(storedFile, "OCR은 이미지 파일과 PDF만 지원합니다.");
        }

        Optional<String> rawText = extractText(storedPath);
        if (rawText.isEmpty()) {
            return unavailableResult(storedFile, "Tesseract OCR을 사용할 수 없습니다. OCR_COMMAND를 확인해 주세요. 현재 값: " + ocrCommand);
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
        return runBestOcr(storedPath);
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
                    Optional<String> pageText = runBestOcr(tempImage);
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

    private Optional<String> runBestOcr(Path inputPath) throws IOException {
        List<String> candidates = new ArrayList<>();
        runTesseract(inputPath).filter(text -> !text.isBlank()).ifPresent(candidates::add);

        BufferedImage image = ImageIO.read(inputPath.toFile());
        if (image != null) {
            Path preprocessed = Files.createTempFile("receipt-ocr-preprocessed-", ".png");
            try {
                ImageIO.write(preprocessForOcr(image), "png", preprocessed.toFile());
                runTesseract(preprocessed).filter(text -> !text.isBlank()).ifPresent(candidates::add);
            } finally {
                Files.deleteIfExists(preprocessed);
            }
        }

        return candidates.stream()
                .max(Comparator.comparingInt(this::scoreOcrCandidate));
    }

    private BufferedImage preprocessForOcr(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        int threshold = calculateOtsuThreshold(source);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y));
                int luminance = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
                int binary = luminance < threshold ? 0 : 255;
                output.setRGB(x, y, new Color(binary, binary, binary).getRGB());
            }
        }
        return output;
    }

    private int calculateOtsuThreshold(BufferedImage source) {
        int[] histogram = new int[256];
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y));
                int luminance = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
                histogram[luminance]++;
            }
        }

        int total = source.getWidth() * source.getHeight();
        float sum = 0;
        for (int index = 0; index < 256; index++) {
            sum += index * histogram[index];
        }

        float sumBackground = 0;
        int weightBackground = 0;
        float maxVariance = 0;
        int threshold = 128;

        for (int index = 0; index < 256; index++) {
            weightBackground += histogram[index];
            if (weightBackground == 0) {
                continue;
            }

            int weightForeground = total - weightBackground;
            if (weightForeground == 0) {
                break;
            }

            sumBackground += (float) index * histogram[index];
            float meanBackground = sumBackground / weightBackground;
            float meanForeground = (sum - sumBackground) / weightForeground;
            float varianceBetween = (float) weightBackground * weightForeground * (meanBackground - meanForeground) * (meanBackground - meanForeground);

            if (varianceBetween > maxVariance) {
                maxVariance = varianceBetween;
                threshold = index;
            }
        }

        return threshold;
    }

    private Optional<String> runTesseract(Path inputPath) {
        Path outputBase = null;
        try {
            outputBase = Files.createTempFile("receipt-ocr-", "");
            Files.deleteIfExists(outputBase);

            for (String commandCandidate : getOcrCommandCandidates()) {
                try {
                    Process process = new ProcessBuilder(
                            commandCandidate,
                            inputPath.toString(),
                            outputBase.toString(),
                            "-l",
                            ocrLanguage,
                            "--psm",
                            "6"
                    ).redirectErrorStream(true).start();

                    boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        log.warn("Tesseract timed out after {} seconds. command={}, input={}", timeoutSeconds, commandCandidate, inputPath);
                        continue;
                    }

                    if (process.exitValue() != 0) {
                        String output = consumeProcessOutput(process);
                        log.warn("Tesseract exited with code {}. command={}, input={}, output={}", process.exitValue(), commandCandidate, inputPath, output);
                        continue;
                    }

                    Path textOutput = Path.of(outputBase + ".txt");
                    if (!Files.exists(textOutput)) {
                        log.warn("Tesseract completed without output file. command={}, input={}", commandCandidate, inputPath);
                        continue;
                    }

                    return Optional.of(Files.readString(textOutput, StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    log.warn("Failed to execute Tesseract. command={}, input={}", commandCandidate, inputPath, exception);
                }
            }

            return Optional.empty();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Tesseract OCR failed before process execution. configuredCommand={}, input={}", ocrCommand, inputPath, exception);
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

    private List<String> getOcrCommandCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (ocrCommand != null && !ocrCommand.isBlank()) {
            candidates.add(ocrCommand);
        }
        if (isWindows()) {
            candidates.add("tesseract");
            candidates.add("tesseract.exe");
        } else {
            candidates.add("tesseract");
        }
        return List.copyOf(candidates);
    }

    private String resolveOcrCommand(String configuredCommand) {
        if (configuredCommand != null && !configuredCommand.isBlank()) {
            try {
                Path configuredPath = Path.of(configuredCommand);
                if (configuredPath.isAbsolute() && Files.exists(configuredPath)) {
                    return configuredPath.toString();
                }
            } catch (InvalidPathException exception) {
                log.warn("OCR_COMMAND is not a plain filesystem path. value={}", configuredCommand);
            }
            if (!"tesseract".equalsIgnoreCase(configuredCommand) && !"tesseract.exe".equalsIgnoreCase(configuredCommand)) {
                return configuredCommand;
            }
        }

        if (isWindows()) {
            return "tesseract";
        }

        return configuredCommand;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String consumeProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        }
        return output.toString();
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

    ParsedReceiptFields parseFields(String rawText) {
        String normalizedText = normalizeOcrText(rawText);
        List<String> rawLines = normalizedText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        List<String> lines = rawLines.stream()
                .map(String::trim)
                .map(this::normalizeLine)
                .filter(line -> !line.isBlank())
                .toList();

        List<ReceiptLineItem> lineItems = extractLineItems(rawLines, lines);
        ReceiptLineItem primaryLineItem = lineItems.isEmpty() ? null : lineItems.get(0);

        LocalDate paymentDate = extractDate(normalizedText).orElse(null);
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
        String paymentMethod = extractPaymentMethod(normalizedText).orElse(null);
        String category = inferCategory(normalizedText, itemName);
        String subcategory = inferSubcategory(category, itemName);

        if (quantity != null && unitPrice != null && amount == null) {
            amount = quantity.multiply(unitPrice);
        }

        return new ParsedReceiptFields(paymentDate, vendor, itemName, quantity, unitPrice, amount, paymentMethod, category, subcategory, lineItems);
    }

    private String normalizeOcrText(String rawText) {
        return rawText
                .replace('\u00A0', ' ')
                .replace('：', ':')
                .replace('．', '.')
                .replace('，', ',')
                .replace('／', '/')
                .replace('－', '-')
                .replaceAll("[|¦]", " ")
                .replaceAll("\r", "");
    }

    private String normalizeLine(String line) {
        String normalized = line.trim()
                .replaceAll("\\s{2,}", " ")
                .replaceAll("([0-9])\\s+([0-9])", "$1$2");
        return normalizeNumericNoise(normalized);
    }

    private String normalizeNumericNoise(String text) {
        return text
                .replaceAll("(?<=[0-9,])[oO](?=[0-9,oO])", "0")
                .replaceAll("(?<=[0-9])[oO]\\b", "0");
    }

    private Optional<LocalDate> extractDate(String rawText) {
        Optional<LocalDate> date = extractDate(rawText, DATE_PATTERN);
        if (date.isPresent()) {
            return date;
        }

        date = extractDate(rawText, KOREAN_DATE_PATTERN);
        if (date.isPresent()) {
            return date;
        }

        Matcher compactMatcher = COMPACT_DATE_PATTERN.matcher(rawText.replaceAll("\\s+", ""));
        if (!compactMatcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(compactMatcher.group(1)),
                    Integer.parseInt(compactMatcher.group(2)),
                    Integer.parseInt(compactMatcher.group(3))
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> extractDate(String rawText, Pattern pattern) {
        Matcher matcher = pattern.matcher(rawText);
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
                .limit(8)
                .filter(this::looksLikeVendorLine)
                .max(Comparator.comparingInt(this::vendorScore))
                .map(String::trim);
    }

    private boolean looksLikeVendorLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        if (line.length() < 2 || line.length() > 40) {
            return false;
        }
        if (containsAny(normalized,
                "receipt", "approved", "total", "subtotal", "vat", "business", "card", "cash", "fax", "tel",
                "영수증", "합계", "총액", "공급가액", "부가세", "카드", "현금", "전화", "사업자", "승인")) {
            return false;
        }
        if (DATE_PATTERN.matcher(line).find() || KOREAN_DATE_PATTERN.matcher(line).find() || COMPACT_DATE_PATTERN.matcher(line).find()) {
            return false;
        }
        long digitCount = line.chars().filter(Character::isDigit).count();
        return digitCount < Math.max(5, line.length() / 2);
    }

    private int vendorScore(String line) {
        int score = 0;
        if (line.matches(".*[가-힣A-Za-z].*")) {
            score += 2;
        }
        if (!line.contains(":")) {
            score += 1;
        }
        if (line.length() >= 3 && line.length() <= 20) {
            score += 2;
        }
        if (line.contains("점") || line.contains("스토어") || line.contains("상사") || line.contains("마트")) {
            score += 2;
        }
        return score;
    }

    private Optional<String> extractItemName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = ITEM_PATTERN.matcher(line);
            if (matcher.find()) {
                return Optional.of(cleanCandidateText(matcher.group(1)));
            }
        }

        return lines.stream()
                .filter(line -> line.length() >= 2)
                .filter(this::looksLikeItemLine)
                .skip(1)
                .findFirst()
                .map(this::cleanCandidateText);
    }

    private boolean looksLikeItemLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        if (containsAny(normalized,
                "receipt", "total", "subtotal", "vat", "card", "cash",
                "영수증", "합계", "총액", "현금", "카드", "부가세", "공급가액")) {
            return false;
        }
        long digitCount = line.chars().filter(Character::isDigit).count();
        return digitCount < Math.max(4, line.length() / 2);
    }

    private List<ReceiptLineItem> extractLineItems(List<String> rawLines, List<String> lines) {
        List<ReceiptLineItem> columnarItems = extractColumnarLineItems(rawLines);
        if (!columnarItems.isEmpty()) {
            return columnarItems;
        }

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

            String itemName = cleanCandidateText(matcher.group(1));
            if (itemName.length() < 2 || containsAny(itemName.toLowerCase(Locale.ROOT), "total", "합계", "subtotal")) {
                continue;
            }

            lineItems.add(new ReceiptLineItem(
                    itemName,
                    null,
                    quantity.get(),
                    null,
                    unitPrice.get(),
                    quantity.get().multiply(unitPrice.get())
            ));
        }

        return lineItems;
    }

    private List<ReceiptLineItem> extractColumnarLineItems(List<String> rawLines) {
        List<ReceiptLineItem> headerDrivenItems = extractHeaderDrivenLineItems(rawLines);
        if (!headerDrivenItems.isEmpty()) {
            return headerDrivenItems;
        }

        List<ReceiptLineItem> lineItems = new ArrayList<>();

        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isBlank() || !containsEnoughNumericSignals(line)) {
                continue;
            }

            String[] columns = line.split("\\s{2,}|\\t+");
            if (columns.length < 4 || columns.length > 6) {
                continue;
            }

            ReceiptLineItem parsed = parseColumnarLineItem(columns);
            if (parsed != null) {
                lineItems.add(parsed);
            }
        }

        return lineItems;
    }

    private List<ReceiptLineItem> extractHeaderDrivenLineItems(List<String> rawLines) {
        int headerIndex = findLineItemHeaderIndex(rawLines);
        if (headerIndex < 0) {
            return List.of();
        }

        List<ReceiptLineItem> lineItems = new ArrayList<>();
        for (int index = headerIndex + 1; index < rawLines.size(); index++) {
            String rawLine = rawLines.get(index).trim();
            if (rawLine.isBlank()) {
                continue;
            }
            if (containsAny(rawLine.toLowerCase(Locale.ROOT), "합계", "총액", "subtotal", "total", "결제", "금액")) {
                break;
            }

            ReceiptLineItem parsed = parseHeaderDrivenLine(rawLine);
            if (parsed != null) {
                lineItems.add(parsed);
            }
        }
        return lineItems;
    }

    private int findLineItemHeaderIndex(List<String> rawLines) {
        for (int index = 0; index < rawLines.size(); index++) {
            String normalized = normalizeForHeader(rawLines.get(index));
            int matchCount = 0;
            if (normalized.contains("품명") || normalized.contains("내역")) matchCount++;
            if (normalized.contains("규격")) matchCount++;
            if (normalized.contains("수량")) matchCount++;
            if (normalized.contains("단위")) matchCount++;
            if (normalized.contains("단가")) matchCount++;
            if (normalized.contains("금액")) matchCount++;
            if (matchCount >= 3) {
                return index;
            }
        }
        return -1;
    }

    private String normalizeForHeader(String line) {
        return line.replaceAll("\\s+", "");
    }

    private ReceiptLineItem parseHeaderDrivenLine(String rawLine) {
        String line = normalizeNumericNoise(rawLine.trim());
        String[] wideColumns = line.split("\\s{2,}|\\t+");
        if (wideColumns.length >= 4) {
            ReceiptLineItem parsedWide = parseColumnarLineItem(wideColumns);
            if (parsedWide != null) {
                return parsedWide;
            }
        }

        String[] tokens = line.split("\\s+");
        if (tokens.length < 4) {
            return null;
        }

        BigDecimal amount = parseDecimal(tokens[tokens.length - 1]).orElse(null);
        BigDecimal unitPrice = parseDecimal(tokens[tokens.length - 2]).orElse(null);
        if (amount == null || unitPrice == null) {
            return null;
        }

        int quantityIndex = -1;
        for (int index = tokens.length - 3; index >= 0; index--) {
            if (parseDecimal(tokens[index]).isPresent()) {
                quantityIndex = index;
                break;
            }
        }
        if (quantityIndex < 0) {
            return null;
        }

        BigDecimal quantity = parseDecimal(tokens[quantityIndex]).orElse(null);
        if (quantity == null) {
            return null;
        }

        String unit = null;
        if (quantityIndex + 1 < tokens.length - 2 && isLikelyUnit(tokens[quantityIndex + 1])) {
            unit = tokens[quantityIndex + 1];
        }

        List<String> prefixTokens = new ArrayList<>();
        for (int index = 0; index < quantityIndex; index++) {
            prefixTokens.add(tokens[index]);
        }
        if (prefixTokens.isEmpty()) {
            return null;
        }

        String itemName = cleanCandidateText(prefixTokens.get(0));
        String spec = prefixTokens.size() > 1
                ? cleanCandidateText(String.join(" ", prefixTokens.subList(1, prefixTokens.size())))
                : null;

        if (itemName.length() < 2) {
            return null;
        }

        return new ReceiptLineItem(itemName, spec, quantity, unit, unitPrice, amount);
    }

    private boolean containsEnoughNumericSignals(String line) {
        long digitCount = line.chars().filter(Character::isDigit).count();
        return digitCount >= 3;
    }

    private ReceiptLineItem parseColumnarLineItem(String[] columns) {
        List<String> values = new ArrayList<>();
        for (String column : columns) {
            String cleaned = cleanCandidateText(normalizeNumericNoise(column));
            if (!cleaned.isBlank()) {
                values.add(cleaned);
            }
        }

        if (values.size() < 4 || values.size() > 6) {
            return null;
        }

        String itemName = cleanCandidateText(values.get(0));
        if (itemName.length() < 2 || containsAny(itemName.toLowerCase(Locale.ROOT), "합계", "총액", "subtotal", "total")) {
            return null;
        }

        String spec = null;
        String unit = null;
        BigDecimal quantity;
        BigDecimal unitPrice;
        BigDecimal amount;

        try {
            if (values.size() == 6) {
                spec = values.get(1);
                quantity = parseDecimal(values.get(2)).orElse(null);
                unit = values.get(3);
                unitPrice = parseDecimal(values.get(4)).orElse(null);
                amount = parseDecimal(values.get(5)).orElse(null);
            } else if (values.size() == 5 && isLikelyUnit(values.get(3))) {
                spec = values.get(1);
                quantity = parseDecimal(values.get(2)).orElse(null);
                unit = values.get(3);
                unitPrice = parseDecimal(values.get(4)).orElse(null);
                amount = quantity != null && unitPrice != null ? quantity.multiply(unitPrice) : null;
            } else if (values.size() == 5) {
                spec = values.get(1);
                quantity = parseDecimal(values.get(2)).orElse(null);
                unitPrice = parseDecimal(values.get(3)).orElse(null);
                amount = parseDecimal(values.get(4)).orElse(null);
            } else {
                quantity = parseDecimal(values.get(1)).orElse(null);
                unitPrice = parseDecimal(values.get(2)).orElse(null);
                amount = parseDecimal(values.get(3)).orElse(null);
            }
        } catch (RuntimeException exception) {
            return null;
        }

        if (quantity == null || unitPrice == null) {
            return null;
        }
        if (amount == null) {
            amount = quantity.multiply(unitPrice);
        }

        return new ReceiptLineItem(itemName, spec, quantity, unit, unitPrice, amount);
    }

    private boolean isLikelyUnit(String value) {
        return UNIT_TOKENS.contains(value.toLowerCase(Locale.ROOT));
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
        List<BigDecimal> priorityCandidates = new ArrayList<>();
        List<BigDecimal> allCandidates = new ArrayList<>();

        for (String line : lines) {
            List<BigDecimal> found = findAmounts(line);
            if (containsAny(line.toLowerCase(Locale.ROOT), "total", "amount", "paid", "합계", "총액", "결제", "청구", "금액")) {
                priorityCandidates.addAll(found);
            }
            allCandidates.addAll(found);
        }

        if (!priorityCandidates.isEmpty()) {
            return priorityCandidates.stream().max(Comparator.naturalOrder());
        }

        return allCandidates.stream().max(Comparator.naturalOrder());
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
            return Optional.of(new BigDecimal(
                    value.replace(",", "")
                            .replace("O", "0")
                            .replace("o", "0")
            ));
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
        if (containsAny(normalized, "transfer", "bank", "계좌", "이체")) {
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
        if (fields.paymentDate() != null) score += 2;
        if (fields.vendor() != null) score += 2;
        if (fields.itemName() != null) score += 2;
        if (fields.quantity() != null) score += 1;
        if (fields.unitPrice() != null) score += 1;
        if (fields.amount() != null) score += 2;
        if (fields.paymentMethod() != null) score += 1;
        if (!fields.lineItems().isEmpty()) score += 2;
        if (!rawText.isBlank()) score += 1;
        if (rawText.lines().count() >= 3) score += 1;
        return Math.min(0.98d, 0.18d + (score * 0.065d));
    }

    private List<String> buildWarnings(ParsedReceiptFields fields) {
        List<String> warnings = new ArrayList<>();
        if (fields.vendor() == null) {
            warnings.add("거래처를 자동으로 인식하지 못했습니다.");
        }
        if (fields.itemName() == null) {
            warnings.add("항목명을 자동으로 인식하지 못했습니다.");
        }
        if (fields.unitPrice() == null && fields.lineItems().isEmpty()) {
            warnings.add("단가를 자동으로 인식하지 못했습니다.");
        }
        if (fields.amount() == null) {
            warnings.add("총 금액을 자동으로 인식하지 못했습니다.");
        }
        if (fields.paymentDate() == null) {
            warnings.add("결제일자를 자동으로 인식하지 못했습니다.");
        }
        if (fields.lineItems().isEmpty()) {
            warnings.add("품목 행을 인식하지 못했습니다. 수량과 단가를 직접 확인해 주세요.");
        }
        return warnings;
    }

    private int scoreOcrCandidate(String text) {
        ParsedReceiptFields fields = parseFields(text);
        int score = 0;
        if (fields.paymentDate() != null) score += 4;
        if (fields.vendor() != null) score += 3;
        if (fields.itemName() != null) score += 3;
        if (fields.amount() != null) score += 4;
        if (!fields.lineItems().isEmpty()) score += 4;
        score += Math.min(5, (int) text.lines().filter(line -> !line.isBlank()).count());
        return score;
    }

    private String cleanCandidateText(String text) {
        return text.trim()
                .replaceAll("\\s{2,}", " ")
                .replaceAll("^[\\-:]+", "")
                .replaceAll("[\\-:]+$", "")
                .trim();
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
            String spec,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {
    }
}
