package com.adminsaas.accounting.web;

import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.domain.ProjectStatus;
import com.adminsaas.accounting.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> getProjects() {
        return projectService.findAll().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable Long id) {
        return ProjectResponse.from(projectService.findById(id));
    }

    @PostMapping
    public ProjectResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectEntity project = new ProjectEntity();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setTotalBudget(request.totalBudget());
        project.setExecutedBudget(BigDecimal.ZERO);
        project.setStatus(request.status());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        return ProjectResponse.from(projectService.save(project));
    }

    @PutMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        ProjectEntity project = new ProjectEntity();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setTotalBudget(request.totalBudget());
        project.setExecutedBudget(request.executedBudget());
        project.setStatus(request.status());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        return ProjectResponse.from(projectService.update(id, project));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id) throws java.io.IOException {
        projectService.delete(id);
    }

    public record CreateProjectRequest(
            @NotBlank String name,
            String description,
            @NotNull BigDecimal totalBudget,
            @NotNull ProjectStatus status,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {}

    public record UpdateProjectRequest(
            @NotBlank String name,
            String description,
            @NotNull BigDecimal totalBudget,
            @NotNull BigDecimal executedBudget,
            @NotNull ProjectStatus status,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {}

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            BigDecimal totalBudget,
            BigDecimal executedBudget,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String lastUpdated
    ) {
        static ProjectResponse from(ProjectEntity entity) {
            return new ProjectResponse(
                    entity.getId(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getTotalBudget(),
                    entity.getExecutedBudget(),
                    entity.getStatus(),
                    entity.getStartDate(),
                    entity.getEndDate(),
                    entity.getLastUpdated() != null ? entity.getLastUpdated().toString() : null
            );
        }
    }
}
