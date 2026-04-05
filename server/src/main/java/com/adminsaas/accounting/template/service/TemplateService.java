package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateStatus;
import com.adminsaas.accounting.template.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final List<TemplateContentParser> templateContentParsers;
    private final List<FieldCandidateExtractor> candidateExtractors;
    private final DocumentTemplateDetector documentTemplateDetector;
    private final ObjectMapper objectMapper;

    public TemplateService(
            TemplateRepository templateRepository,
            List<TemplateContentParser> templateContentParsers,
            List<FieldCandidateExtractor> candidateExtractors,
            DocumentTemplateDetector documentTemplateDetector,
            ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.templateContentParsers = templateContentParsers;
        this.candidateExtractors = candidateExtractors;
        this.documentTemplateDetector = documentTemplateDetector;
        this.objectMapper = objectMapper;
    }

    public DocumentTemplate upload(MultipartFile file, String structuredContentJson) throws IOException {
        NormalizedTemplateContent structuredContent = structuredContentJson == null || structuredContentJson.isBlank()
                ? parseFromFile(file)
                : objectMapper.readValue(structuredContentJson, NormalizedTemplateContent.class);

        TemplateDocumentType documentType = documentTemplateDetector.detect(structuredContent);
        DocumentTemplate template = new DocumentTemplate(
                UUID.randomUUID().toString(),
                file.getOriginalFilename() == null ? "template.bin" : file.getOriginalFilename(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                documentType,
                TemplateStatus.UPLOADED,
                OffsetDateTime.now(),
                structuredContent,
                new TemplateExtractionSummary(structuredContent.labels().size(), structuredContent.tables().size(), 0, 0),
                new TemplateMappingSchema(null, documentType, List.of(), List.of())
        );
        return templateRepository.save(template);
    }

    public DocumentTemplate uploadJson(String filename, String contentType, NormalizedTemplateContent structuredContent) {
        TemplateDocumentType documentType = documentTemplateDetector.detect(structuredContent);
        DocumentTemplate template = new DocumentTemplate(
                UUID.randomUUID().toString(),
                filename,
                contentType,
                0L,
                documentType,
                TemplateStatus.UPLOADED,
                OffsetDateTime.now(),
                structuredContent,
                new TemplateExtractionSummary(structuredContent.labels().size(), structuredContent.tables().size(), 0, 0),
                new TemplateMappingSchema(null, documentType, List.of(), List.of())
        );
        return templateRepository.save(template);
    }

    public TemplateCandidateRecommendation extract(String templateId) {
        DocumentTemplate template = getTemplate(templateId);
        TemplateCandidateRecommendation recommendation = recommend(template);
        DocumentTemplate updated = new DocumentTemplate(
                template.id(),
                template.filename(),
                template.contentType(),
                template.size(),
                template.documentType(),
                TemplateStatus.EXTRACTED,
                template.uploadedAt(),
                template.structuredContent(),
                recommendation.summary(),
                recommendation.schema()
        );
        templateRepository.save(updated);
        return recommendation;
    }

    public TemplateCandidateRecommendation recommend(DocumentTemplate template) {
        FieldCandidateExtractor extractor = resolveExtractor(template.documentType());
        return extractor.extract(template.id(), template.structuredContent());
    }

    private FieldCandidateExtractor resolveExtractor(TemplateDocumentType documentType) {
        return candidateExtractors.stream()
                .filter(candidateExtractor -> candidateExtractor.supports(documentType))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "감지된 템플릿 유형에 사용할 추출기가 없습니다."));
    }

    public TemplateMappingSchema updateMappings(String templateId, TemplateMappingSchema mappingSchema) {
        DocumentTemplate template = getTemplate(templateId);
        TemplateMappingSchema normalizedSchema = new TemplateMappingSchema(
                templateId,
                template.documentType(),
                mappingSchema.fields() == null ? List.of() : mappingSchema.fields(),
                mappingSchema.tables() == null ? List.of() : mappingSchema.tables()
        );
        DocumentTemplate updated = new DocumentTemplate(
                template.id(),
                template.filename(),
                template.contentType(),
                template.size(),
                template.documentType(),
                TemplateStatus.MAPPED,
                template.uploadedAt(),
                template.structuredContent(),
                template.extractionSummary(),
                normalizedSchema
        );
        templateRepository.save(updated);
        return normalizedSchema;
    }

    public TemplateMappingSchema getMappings(String templateId) {
        return getTemplate(templateId).mappingSchema();
    }

    public DocumentTemplate getTemplate(String templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없습니다."));
    }

    private NormalizedTemplateContent parseFromFile(MultipartFile file) throws IOException {
        return templateContentParsers.stream()
                .filter(parser -> parser.supports(file))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 파일 형식은 현재 처리할 수 없습니다."))
                .parse(file);
    }
}
