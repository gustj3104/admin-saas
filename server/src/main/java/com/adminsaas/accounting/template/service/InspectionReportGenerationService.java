package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.service.ProjectTemplateLoader;
import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportExportJob;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportGeneratedDocument;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class InspectionReportGenerationService {

    private final TemplateService templateService;
    private final ProjectTemplateLoader projectTemplateLoader;
    private final InspectionReportGroupingService groupingService;
    private final InspectionReportDocumentAssembler documentAssembler;

    public InspectionReportGenerationService(TemplateService templateService,
                                             ProjectTemplateLoader projectTemplateLoader,
                                             InspectionReportGroupingService groupingService,
                                             InspectionReportDocumentAssembler documentAssembler) {
        this.templateService = templateService;
        this.projectTemplateLoader = projectTemplateLoader;
        this.groupingService = groupingService;
        this.documentAssembler = documentAssembler;
    }

    public InspectionReportExportJob generate(String templateId,
                                              InspectionReportExportMode mode,
                                              List<InspectionReportReceiptEntry> entries) {
        DocumentTemplate template = resolveTemplate(templateId);
        return generate(template, mode, entries);
    }

    public InspectionReportExportJob generate(DocumentTemplate template,
                                              InspectionReportExportMode mode,
                                              List<InspectionReportReceiptEntry> entries) {
        if (template.documentType() != TemplateDocumentType.INSPECTION_REPORT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문서 생성은 물품검수조서 템플릿에서만 지원됩니다.");
        }

        List<InspectionReportReceiptEntry> safeEntries = entries == null ? List.of() : entries;
        List<com.adminsaas.accounting.template.domain.InspectionReportExportGroup> groups = groupingService.group(safeEntries, mode);
        List<InspectionReportGeneratedDocument> documents = documentAssembler.assemble(template, groups);
        return new InspectionReportExportJob(template.id(), mode, groups, documents);
    }

    private DocumentTemplate resolveTemplate(String templateId) {
        ProjectTemplateReference projectTemplateReference = parseProjectTemplateReference(templateId);
        if (projectTemplateReference != null) {
            try {
                return projectTemplateLoader.load(projectTemplateReference.projectId(), projectTemplateReference.relativePath());
            } catch (java.io.IOException exception) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트 템플릿을 찾을 수 없습니다.");
            }
        }
        return templateService.getTemplate(templateId);
    }

    private ProjectTemplateReference parseProjectTemplateReference(String templateId) {
        if (templateId == null || !templateId.startsWith("project-")) {
            return null;
        }

        int separatorIndex = templateId.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == templateId.length() - 1) {
            return null;
        }

        String projectIdToken = templateId.substring("project-".length(), separatorIndex);
        try {
            return new ProjectTemplateReference(Long.parseLong(projectIdToken), templateId.substring(separatorIndex + 1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record ProjectTemplateReference(
            Long projectId,
            String relativePath
    ) {
    }
}
