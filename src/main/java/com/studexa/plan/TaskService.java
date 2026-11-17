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
 * Server-backed tasks service. Replaces the local-only Zustand mock store
 * so tasks sync across devices and can be verified for streak/XP awards.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProfileRepository profileRepository;

    public TaskService(TaskRepository taskRepository, ProfileRepository profileRepository) {
        this.taskRepository = taskRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> list(UUID userId) {
        return taskRepository.findByUserIdOrderByTaskDateAsc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listByDate(UUID userId, LocalDate taskDate) {
        return taskRepository.findByUserIdAndTaskDateOrderByTaskDateAsc(userId, taskDate).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDto get(UUID userId, UUID id) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Task not found."));
        return toDto(task);
    }

    @Transactional
    public TaskDto create(UUID userId, TaskRequest request) {
        profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found."));

        Instant now = Instant.now();
        Task task = Task.create(userId, request.title(), request.taskDate());
        task = taskRepository.save(task);
        return toDto(task);
    }

    @Transactional
    public TaskDto update(UUID userId, UUID id, TaskRequest request) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Task not found."));

        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.taskDate() != null) {
            task.setTaskDate(request.taskDate());
        }
        if (request.done() != null) {
            task.setDone(request.done());
        }

        task = taskRepository.save(task);
        return toDto(task);
    }

    @Transactional
    public TaskDto toggle(UUID userId, UUID id) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Task not found."));
        task.setDone(!task.isDone());
        task = taskRepository.save(task);
        return toDto(task);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        boolean exists = taskRepository.findById(id)
                .map(t -> t.getUserId().equals(userId))
                .orElse(false);
        if (!exists) {
            throw ApiException.notFound("Task not found.");
        }
        taskRepository.deleteById(id);
    }

    private TaskDto toDto(Task task) {
        return new TaskDto(
                task.getId(),
                task.getTitle(),
                task.getTaskDate(),
                task.isDone(),
                task.getUpdatedAt(),
                task.getCreatedAt());
    }
}