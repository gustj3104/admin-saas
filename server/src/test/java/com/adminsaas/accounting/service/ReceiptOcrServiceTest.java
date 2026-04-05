package com.adminsaas.accounting.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ReceiptOcrServiceTest {

    private final ReceiptOcrService service = new ReceiptOcrService(mock(FileStorageService.class), "tesseract", "kor+eng", 20);

    @Test
    void parsesKoreanReceiptFieldsFromNormalizedText() {
        String rawText = """
                스타벅스 강남점
                2026년 04월 05일
                카페라떼
                수량 2
                단가 4,500
                합계 9,000
                카드
                """;

        ReceiptOcrService.ParsedReceiptFields fields = service.parseFields(rawText);

        assertEquals(LocalDate.of(2026, 4, 5), fields.paymentDate());
        assertEquals("스타벅스 강남점", fields.vendor());
        assertEquals("카페라떼", fields.itemName());
        assertEquals(new BigDecimal("2"), fields.quantity());
        assertEquals(new BigDecimal("4500"), fields.unitPrice());
        assertEquals(new BigDecimal("9000"), fields.amount());
        assertEquals("card", fields.paymentMethod());
    }

    @Test
    void correctsCommonOcrDigitNoiseInAmounts() {
        String rawText = """
                테스트상사
                20260405
                복사용지
                수량 1
                단가 12,OOO
                총액 12,OOO
                """;

        ReceiptOcrService.ParsedReceiptFields fields = service.parseFields(rawText);

        assertEquals(LocalDate.of(2026, 4, 5), fields.paymentDate());
        assertEquals(new BigDecimal("12000"), fields.unitPrice());
        assertEquals(new BigDecimal("12000"), fields.amount());
    }

    @Test
    void extractsLineItemsWhenPresent() {
        String rawText = """
                오피스디포
                A4용지 2 x 3500
                총액 7000
                """;

        ReceiptOcrService.ParsedReceiptFields fields = service.parseFields(rawText);

        assertNotNull(fields.lineItems());
        assertEquals(1, fields.lineItems().size());
        assertEquals("A4용지", fields.lineItems().get(0).itemName());
        assertEquals(null, fields.lineItems().get(0).spec());
        assertEquals(null, fields.lineItems().get(0).unit());
        assertEquals(new BigDecimal("7000"), fields.amount());
    }

    @Test
    void extractsMultipleColumnarLineItemsDynamically() {
        String rawText = """
                문구마트
                품명    규격    수량    단위    단가    금액
                A4용지    80g    2    EA    3500    7000
                볼펜    0.7    3    EA    1000    3000
                총액 10000
                """;

        ReceiptOcrService.ParsedReceiptFields fields = service.parseFields(rawText);

        assertEquals(2, fields.lineItems().size());
        assertEquals("A4용지", fields.lineItems().get(0).itemName());
        assertEquals("80g", fields.lineItems().get(0).spec());
        assertEquals(new BigDecimal("2"), fields.lineItems().get(0).quantity());
        assertEquals("EA", fields.lineItems().get(0).unit());
        assertEquals(new BigDecimal("3500"), fields.lineItems().get(0).unitPrice());
        assertEquals(new BigDecimal("7000"), fields.lineItems().get(0).amount());
        assertEquals("볼펜", fields.lineItems().get(1).itemName());
        assertEquals(new BigDecimal("10000"), fields.amount());
    }

    @Test
    void extractsHeaderDrivenLineItemsFromWhitespaceSeparatedRows() {
        String rawText = """
                테스트상사
                품명 규격 수량 단위 단가 금액
                복사용지 A4 2 EA 3500 7000
                파일홀더 대 3 EA 1000 3000
                총액 10000
                """;

        ReceiptOcrService.ParsedReceiptFields fields = service.parseFields(rawText);

        assertEquals(2, fields.lineItems().size());
        assertEquals("복사용지", fields.lineItems().get(0).itemName());
        assertEquals("A4", fields.lineItems().get(0).spec());
        assertEquals("EA", fields.lineItems().get(0).unit());
        assertEquals(new BigDecimal("3000"), fields.lineItems().get(1).amount());
        assertEquals(new BigDecimal("10000"), fields.amount());
    }
}
