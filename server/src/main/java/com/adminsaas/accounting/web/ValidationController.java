package com.adminsaas.accounting.web;

import com.adminsaas.accounting.domain.ValidationResultEntity;
import com.adminsaas.accounting.domain.ValidationSeverity;
import com.adminsaas.accounting.service.ValidationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/validations")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping
    public List<ValidationResponse> getValidations(@RequestParam Long projectId) {
        return validationService.findByProjectId(projectId).stream().map(ValidationResponse::from).toList();
    }

    @PostMapping("/run")
    public List<ValidationResponse> runValidations(@RequestParam Long projectId) {
        return validationService.run(projectId).stream().map(ValidationResponse::from).toList();
    }

    @PostMapping("/{id}/resolve")
    public ValidationResponse resolveValidation(@PathVariable Long id, @Valid @RequestBody ResolveValidationRequest request) {
        return ValidationResponse.from(validationService.resolve(id, request.resolutionNote()));
    }

    public record ValidationResponse(
            Long id,
            String type,
            String category,
            String description,
            ValidationSeverity severity,
            LocalDate date,
            String linkTo,
            boolean resolved,
            String resolutionNote,
            LocalDate resolvedDate
    ) {
        static ValidationResponse from(ValidationResultEntity entity) {
            return new ValidationResponse(
                    entity.getId(),
                    entity.getType(),
                    entity.getCategory(),
                    entity.getDescription(),
                    entity.getSeverity(),
                    entity.getResultDate(),
                    entity.getLinkTo(),
                    entity.isResolved(),
                    entity.getResolutionNote(),
                    entity.getResolvedDate()
            );
        }
    }

    public record ResolveValidationRequest(String resolutionNote) {
    }
}
