package com.adminsaas.accounting.repository;

import com.adminsaas.accounting.domain.ValidationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidationResultRepository extends JpaRepository<ValidationResultEntity, Long> {
    List<ValidationResultEntity> findByProjectIdOrderByResultDateDescIdAsc(Long projectId);
    List<ValidationResultEntity> findByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
}
