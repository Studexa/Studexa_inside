package com.studexa.subject;

import java.util.List;
import java.util.UUID;
import java.text.Normalizer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.subject.dto.CreateSubjectRequest;
import com.studexa.subject.dto.SubjectResponse;
import com.studexa.subject.dto.UpdateSubjectRequest;

/**
 * Every method is scoped to the logged-in user; another user's subject id
 * is reported as 404 so ids cannot be probed.
 */
@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> list(UUID userId, Boolean archived) {
        List<Subject> subjects = archived == null
                ? subjectRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                : subjectRepository.findAllByUserIdAndArchivedOrderByCreatedAtAsc(userId, archived);
        return subjects.stream().map(SubjectResponse::from).toList();
    }

    @Transactional
    public SubjectResponse create(UUID userId, CreateSubjectRequest request) {
        String name = normalizeText(requiredName(request.name()));
        Subject subject = Subject.create(
                userId,
                name,
                colorOrDefault(request.color()),
                blankToNull(request.icon()));
        return SubjectResponse.from(subjectRepository.save(subject));
    }

    @Transactional
    public SubjectResponse update(UUID userId, UUID subjectId, UpdateSubjectRequest request) {
        Subject subject = requireOwned(userId, subjectId);

        if (request.name() != null) {
            subject.setName(normalizeText(requiredName(request.name())));
        }
        if (request.color() != null) {
            subject.setColor(colorOrDefault(request.color()));
        }
        if (request.icon() != null) {
            subject.setIcon(blankToNull(request.icon()));
        }
        if (request.archived() != null) {
            subject.setArchived(request.archived());
        }

        return SubjectResponse.from(subjectRepository.save(subject));
    }

    @Transactional
    public void delete(UUID userId, UUID subjectId) {
        subjectRepository.delete(requireOwned(userId, subjectId));
    }

    private Subject requireOwned(UUID userId, UUID subjectId) {
        return subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> ApiException.notFound("Subject not found."));
    }

    private static String requiredName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("INVALID_NAME", "Subject name cannot be empty.");
        }
        return trimmed;
    }

    private static String colorOrDefault(String color) {
        String trimmed = color == null ? "" : color.trim();
        return trimmed.isEmpty() ? Subject.DEFAULT_COLOR : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Normalizes text to Unicode NFC so visually identical Khmer strings
     * compare equal (BUG-G03).
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
