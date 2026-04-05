package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.InspectionReportExportGroup;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InspectionReportGroupingService {

    private static final String UNKNOWN_DATE_BUCKET = "unknown-date";
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    public List<InspectionReportExportGroup> group(List<InspectionReportReceiptEntry> entries, InspectionReportExportMode mode) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case PER_RECEIPT -> groupPerReceipt(entries);
            case BY_DATE -> groupByDate(entries);
            case ALL -> List.of(new InspectionReportExportGroup("all", null, sortEntries(entries)));
        };
    }

    private List<InspectionReportExportGroup> groupPerReceipt(List<InspectionReportReceiptEntry> entries) {
        List<InspectionReportReceiptEntry> sorted = sortEntries(entries);
        List<InspectionReportExportGroup> groups = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            InspectionReportReceiptEntry entry = sorted.get(index);
            String groupKey = entry.receiptId() == null || entry.receiptId().isBlank()
                    ? "receipt-" + index
                    : entry.receiptId();
            groups.add(new InspectionReportExportGroup(groupKey, normalizePaymentDate(entry.paymentDate()), List.of(entry)));
        }
        return groups;
    }

    private List<InspectionReportExportGroup> groupByDate(List<InspectionReportReceiptEntry> entries) {
        Map<String, List<InspectionReportReceiptEntry>> grouped = new LinkedHashMap<>();
        for (InspectionReportReceiptEntry entry : sortEntries(entries)) {
            String normalizedDate = normalizePaymentDate(entry.paymentDate());
            String key = normalizedDate == null ? UNKNOWN_DATE_BUCKET : normalizedDate;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry<String, List<InspectionReportReceiptEntry>>::getKey, this::compareGroupKeys))
                .map(entry -> new InspectionReportExportGroup(
                        entry.getKey(),
                        UNKNOWN_DATE_BUCKET.equals(entry.getKey()) ? null : entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private int compareGroupKeys(String left, String right) {
        if (UNKNOWN_DATE_BUCKET.equals(left) && UNKNOWN_DATE_BUCKET.equals(right)) {
            return 0;
        }
        if (UNKNOWN_DATE_BUCKET.equals(left)) {
            return 1;
        }
        if (UNKNOWN_DATE_BUCKET.equals(right)) {
            return -1;
        }
        return left.compareTo(right);
    }

    private List<InspectionReportReceiptEntry> sortEntries(List<InspectionReportReceiptEntry> entries) {
        return entries.stream()
                .sorted(Comparator
                        .comparingInt(InspectionReportReceiptEntry::receiptOrder)
                        .thenComparing(entry -> entry.receiptId() == null ? "" : entry.receiptId()))
                .toList();
    }

    public String normalizePaymentDate(String paymentDate) {
        if (paymentDate == null || paymentDate.isBlank()) {
            return null;
        }

        String trimmed = paymentDate.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter).toString();
            } catch (DateTimeParseException ignored) {
            }
        }

        String normalized = trimmed.replaceAll("\\s+", "")
                .replace("년", "-")
                .replace("월", "-")
                .replace("일", "");
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d")).toString();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
