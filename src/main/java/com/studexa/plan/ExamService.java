package com.studexa.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.user.ProfileRepository;

/**
 * Server-backed exams service. Replaces the local-only Zustand mock store.
 */
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final ProfileRepository profileRepository;

    public ExamService(ExamRepository examRepository, ProfileRepository profileRepository) {
        this.examRepository = examRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<ExamDto> list(UUID userId) {
        return examRepository.findByUserIdOrderByExamDateAsc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamDto get(UUID userId, UUID id) {
        Exam exam = examRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Exam not found."));
        return toDto(exam);
    }

    @Transactional
    public ExamDto create(UUID userId, ExamRequest request) {
        profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found."));

        Instant now = Instant.now();
        Exam exam = Exam.create(userId, request.title(), request.subject(),
                request.examDate(), request.importance());
        exam = examRepository.save(exam);
        return toDto(exam);
    }

    @Transactional
    public ExamDto update(UUID userId, UUID id, ExamRequest request) {
        Exam exam = examRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Exam not found."));

        if (request.title() != null) {
            exam.setTitle(request.title());
        }
        if (request.subject() != null) {
            exam.setSubject(request.subject());
        }
        if (request.examDate() != null) {
            exam.setExamDate(request.examDate());
        }
        if (request.importance() != null) {
            exam.setImportance(request.importance());
        }

        exam = examRepository.save(exam);
        return toDto(exam);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (!examRepository.existsByIdAndUserId(id, userId)) {
            throw ApiException.notFound("Exam not found.");
        }
        examRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ExamDto> listByDateRange(UUID userId, LocalDate start, LocalDate end) {
        return examRepository.findByUserIdAndExamDateBetweenOrderByExamDateAsc(userId, start, end).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ExamDto toDto(Exam exam) {
        return new ExamDto(
                exam.getId(),
                exam.getTitle(),
                exam.getSubject(),
                exam.getExamDate(),
                exam.getImportance().name(),
                exam.getUpdatedAt(),
                exam.getCreatedAt());
    }
}
