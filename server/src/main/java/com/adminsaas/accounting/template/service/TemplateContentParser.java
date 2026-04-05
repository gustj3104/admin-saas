package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface TemplateContentParser {

    boolean supports(MultipartFile file);

    NormalizedTemplateContent parse(MultipartFile file) throws IOException;
}
