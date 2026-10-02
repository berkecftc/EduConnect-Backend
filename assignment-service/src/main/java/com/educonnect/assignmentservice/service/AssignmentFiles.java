package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.assignmentservice.repository.SubmissionVersionRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class AssignmentFiles {

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;

    public AssignmentFiles(SubmissionRepository submissionRepository, SubmissionVersionRepository versionRepository) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
    }

    public List<String> of(Collection<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<String> files = new LinkedHashSet<>();
        assignments.forEach(assignment -> files.add(assignment.getFileUrl()));
        List<AssignmentSubmission> submissions = submissionRepository.findByAssignmentIdIn(
                assignments.stream().map(Assignment::getId).toList());
        submissions.forEach(submission -> files.add(submission.getSubmissionFileUrl()));
        if (!submissions.isEmpty()) {
            versionRepository.findBySubmissionIdIn(submissions.stream().map(AssignmentSubmission::getId).toList())
                    .forEach(version -> files.add(version.getFileUrl()));
        }
        files.remove(null);
        return List.copyOf(files);
    }
}
