package com.studexa.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;

import jakarta.validation.Valid;

/**
 * Task (todo) management. Tasks belong to the authenticated user and are
 * scoped by date so the planner can query "tasks for today" etc.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate date) {
        UUID userId = CurrentUser.id(jwt);
        if (date != null) {
            return taskService.listByDate(userId, date);
        }
        return taskService.list(userId);
    }

    @GetMapping("/{id}")
    public TaskDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return taskService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskRequest request) {
        return taskService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public TaskDto update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody TaskRequest request) {
        return taskService.update(CurrentUser.id(jwt), id, request);
    }

    @PutMapping("/{id}/toggle")
    public TaskDto toggle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return taskService.toggle(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        taskService.delete(CurrentUser.id(jwt), id);
    }
}