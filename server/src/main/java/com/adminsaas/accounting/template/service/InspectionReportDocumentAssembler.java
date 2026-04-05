package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportDocumentHead;
import com.adminsaas.accounting.template.domain.InspectionReportExportGroup;
import com.adminsaas.accounting.template.domain.InspectionReportGeneratedDocument;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InspectionReportDocumentAssembler {

    private static final String DEFAULT_TITLE = "물품검수조서";

    private final InspectionReportBlockRenderer blockRenderer;

    public InspectionReportDocumentAssembler(InspectionReportBlockRenderer blockRenderer) {
        this.blockRenderer = blockRenderer;
    }

    public List<InspectionReportGeneratedDocument> assemble(DocumentTemplate template, List<InspectionReportExportGroup> groups) {
        InspectionReportDocumentHead head = new InspectionReportDocumentHead(resolveHeadTitle(template));
        return groups.stream()
                .map(group -> new InspectionReportGeneratedDocument(
                        group.groupKey(),
                        buildDocumentTitle(group),
                        head,
                        group.entries().stream()
                                .map(entry -> blockRenderer.render(entry, group.normalizedDate()))
                                .toList()
                ))
                .toList();
    }

    private String resolveHeadTitle(DocumentTemplate template) {
        return template.structuredContent().blocks().stream()
                .filter(block -> "heading".equalsIgnoreCase(block.type()) || "title".equalsIgnoreCase(block.type()))
                .map(block -> block.text() == null ? "" : block.text().trim())
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse(DEFAULT_TITLE);
    }

    private String buildDocumentTitle(InspectionReportExportGroup group) {
        if ("all".equals(group.groupKey())) {
            return DEFAULT_TITLE + "_전체";
        }
        if ("unknown-date".equals(group.groupKey())) {
            return DEFAULT_TITLE + "_unknown-date";
        }
        return DEFAULT_TITLE + "_" + group.groupKey();
    }
}
