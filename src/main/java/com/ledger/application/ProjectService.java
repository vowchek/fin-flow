package com.ledger.application;

import com.ledger.api.dto.ProjectRequest;
import com.ledger.api.dto.ProjectResponse;
import com.ledger.domain.Project;
import com.ledger.infrastructure.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(boolean includeArchived) {
        List<Project> source = includeArchived
                ? projects.findAll()
                : projects.findAllByArchivedFalseOrderByNameAsc();
        return source.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Project project = new Project(UUID.randomUUID(), request.name().trim(), trimToNull(request.description()));
        return toResponse(projects.save(project));
    }

    @Transactional
    public ProjectResponse update(UUID id, ProjectRequest request) {
        Project project = require(id);
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse archive(UUID id) {
        Project project = require(id);
        project.setArchived(true);
        return toResponse(project);
    }

    public Project require(UUID id) {
        return projects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    public Project requireOptional(UUID id) {
        if (id == null) {
            return null;
        }
        return require(id);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.isArchived(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
