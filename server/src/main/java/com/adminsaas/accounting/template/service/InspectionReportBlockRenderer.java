package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.InspectionReportReceiptBlock;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import org.springframework.stereotype.Component;

@Component
public class InspectionReportBlockRenderer {

    public InspectionReportReceiptBlock render(InspectionReportReceiptEntry entry, String normalizedPaymentDate) {
        return new InspectionReportReceiptBlock(
                entry.receiptId(),
                entry.title(),
                entry.paymentDate(),
                normalizedPaymentDate,
                entry.vendor(),
                entry.inspector(),
                entry.contractAmount(),
                entry.receiptImagePath(),
                entry.receiptOrder(),
                entry.itemRows() == null ? java.util.List.of() : entry.itemRows()
        );
    }
}
