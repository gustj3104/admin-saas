package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.ValidationResultEntity;
import com.adminsaas.accounting.repository.ValidationResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class ValidationService {

    private final ValidationResultRepository validationResultRepository;

    public ValidationService(ValidationResultRepository validationResultRepository) {
        this.validationResultRepository = validationResultRepository;
    }

    public List<ValidationResultEntity> findByProjectId(Long projectId) {
        return validationResultRepository.findByProjectIdOrderByResultDateDescIdAsc(projectId);
    }

    public List<ValidationResultEntity> run(Long projectId) {
        return findByProjectId(projectId);
    }

    public ValidationResultEntity resolve(Long id, String resolutionNote) {
        ValidationResultEntity validation = validationResultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Validation result not found"));
        validation.setResolved(true);
        validation.setResolutionNote(resolutionNote);
        validation.setResolvedDate(LocalDate.now());
        return validationResultRepository.save(validation);
    }
}
