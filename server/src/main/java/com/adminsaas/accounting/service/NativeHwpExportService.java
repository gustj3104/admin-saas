package com.adminsaas.accounting.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class NativeHwpExportService {

    private final WordExportService wordExportService;
    private final HwpExportService hwpExportService;
    private final String converterCommand;
    private final Duration timeout;

    public NativeHwpExportService(WordExportService wordExportService,
                                  HwpExportService hwpExportService,
                                  @Value("${app.hwp.converter.command:}") String converterCommand,
                                  @Value("${app.hwp.converter.timeout-seconds:60}") long timeoutSeconds) {
        this.wordExportService = wordExportService;
        this.hwpExportService = hwpExportService;
        this.converterCommand = converterCommand == null ? "" : converterCommand.trim();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public byte[] createEvidenceDocument(WordExportService.EvidenceExportPayload wordPayload,
                                         HwpExportService.HwpEvidencePayload hwpPayload) throws IOException {
        return convert("evidence-document", wordExportService.createEvidenceDocument(wordPayload), hwpExportService.createEvidenceDocument(hwpPayload));
    }

    public byte[] createSettlementDocument(WordExportService.SettlementExportPayload wordPayload,
                                           HwpExportService.HwpSettlementPayload hwpPayload) throws IOException {
        return convert("settlement-report", wordExportService.createSettlementDocument(wordPayload), hwpExportService.createSettlementDocument(hwpPayload));
    }

    private byte[] convert(String baseFilename, byte[] docxBytes, byte[] hmlBytes) throws IOException {
        ensureConfigured();

        Path workDir = Files.createTempDirectory("admin-saas-hwp-");
        Path docxFile = workDir.resolve(baseFilename + ".docx");
        Path hmlFile = workDir.resolve(baseFilename + ".hml");
        Path outputFile = workDir.resolve(baseFilename + ".hwp");

        try {
            Files.write(docxFile, docxBytes);
            Files.write(hmlFile, hmlBytes);
            runConverter(workDir, docxFile, hmlFile, outputFile);

            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "HWP 변환은 완료됐지만 출력 파일이 생성되지 않았습니다.");
            }

            return Files.readAllBytes(outputFile);
        } finally {
            deleteQuietly(outputFile);
            deleteQuietly(hmlFile);
            deleteQuietly(docxFile);
            deleteQuietly(workDir);
        }
    }

    private void ensureConfigured() {
        if (converterCommand.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "네이티브 HWP 변환이 설정되지 않았습니다. 먼저 HWP_CONVERTER_COMMAND를 설정해 주세요."
            );
        }
    }

    private void runConverter(Path workDir, Path docxFile, Path hmlFile, Path outputFile) throws IOException {
        String command = converterCommand
                .replace("{docx}", docxFile.toAbsolutePath().toString())
                .replace("{hml}", hmlFile.toAbsolutePath().toString())
                .replace("{output}", outputFile.toAbsolutePath().toString())
                .replace("{workdir}", workDir.toAbsolutePath().toString());

        List<String> shellCommand = buildShellCommand(command);
        ProcessBuilder builder = new ProcessBuilder(shellCommand);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        try {
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes());

            if (!finished) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "HWP 변환 시간이 초과되었습니다.");
            }

            if (process.exitValue() != 0) {
                String message = output == null || output.trim().isBlank()
                        ? "HWP 변환에 실패했습니다."
                        : "HWP 변환에 실패했습니다: " + output.trim();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "HWP 변환이 중단되었습니다.");
        }
    }

    private List<String> buildShellCommand(String command) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return List.of("cmd", "/c", command);
        }
        return List.of("sh", "-lc", command);
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }
}
