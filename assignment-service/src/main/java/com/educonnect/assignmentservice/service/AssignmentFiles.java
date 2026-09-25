package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class AssignmentFiles {

    private final SubmissionRepository submissionRepository;

    public AssignmentFiles(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public List<String> of(Collection<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        assignments.forEach(assignment -> files.add(assignment.getFileUrl()));
        submissionRepository.findByAssignmentIdIn(assignments.stream().map(Assignment::getId).toList()).stream()
                .map(AssignmentSubmission::getSubmissionFileUrl)
                .forEach(files::add);
        return files;
    }
}
