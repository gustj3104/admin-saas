package com.adminsaas.accounting.service;

import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateStatus;
import com.adminsaas.accounting.template.service.DocumentTemplateDetector;
import com.adminsaas.accounting.template.service.TemplateContentParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ProjectTemplateLoader {

    private final FileStorageService fileStorageService;
    private final List<TemplateContentParser> templateContentParsers;
    private final DocumentTemplateDetector documentTemplateDetector;

    public ProjectTemplateLoader(FileStorageService fileStorageService,
                                 List<TemplateContentParser> templateContentParsers,
                                 DocumentTemplateDetector documentTemplateDetector) {
        this.fileStorageService = fileStorageService;
        this.templateContentParsers = templateContentParsers;
        this.documentTemplateDetector = documentTemplateDetector;
    }

    public DocumentTemplate load(Long projectId, String relativePath) throws IOException {
        FileStorageService.StoredProjectFile storedFile = fileStorageService.get(projectId, relativePath);
        FileStorageService.ResourceFile file = fileStorageService.load(projectId, relativePath);
        InMemoryMultipartFile multipartFile = new InMemoryMultipartFile(
                storedFile.originalFilename(),
                file.contentType() == null ? "application/octet-stream" : file.contentType(),
                file.content()
        );

        NormalizedTemplateContent structuredContent = templateContentParsers.stream()
                .filter(parser -> parser.supports(multipartFile))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 프로젝트 템플릿을 처리할 수 없습니다."))
                .parse(multipartFile);
        TemplateDocumentType documentType = documentTemplateDetector.detect(structuredContent);

        return new DocumentTemplate(
                "project-" + projectId + ":" + relativePath,
                storedFile.originalFilename(),
                multipartFile.getContentType(),
                storedFile.size(),
                documentType,
                TemplateStatus.UPLOADED,
                OffsetDateTime.now(),
                structuredContent,
                new TemplateExtractionSummary(structuredContent.labels().size(), structuredContent.tables().size(), 0, 0),
                new TemplateMappingSchema(null, documentType, List.of(), List.of())
        );
    }

    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
