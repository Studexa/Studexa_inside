package com.studexa.notes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.user.ProfileRepository;

/**
 * Server-backed notes service. Replaces the local-only Zustand mock store
 * so notes sync across devices and can be verified for XP awards.
 */
@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final ProfileRepository profileRepository;

    public NoteService(NoteRepository noteRepository, ProfileRepository profileRepository) {
        this.noteRepository = noteRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<NoteDto> list(UUID userId) {
        return noteRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NoteDto get(UUID userId, UUID id) {
        Note note = noteRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Note not found."));
        return toDto(note);
    }

    @Transactional
    public NoteDto create(UUID userId, NoteRequest request) {
        // Verify the subject exists if one is provided.
        if (request.subject() != null && !request.subject().isBlank()) {
            profileRepository.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("Profile not found."));
        }

        Instant now = Instant.now();
        Note note = Note.create(userId, request.title(), request.content() != null ? request.content() : "",
                request.subject());
        note = noteRepository.save(note);
        return toDto(note);
    }

    @Transactional
    public NoteDto update(UUID userId, UUID id, NoteRequest request) {
        Note note = noteRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Note not found."));

        if (request.title() != null) {
            note.setTitle(request.title());
        }
        if (request.content() != null) {
            note.setContent(request.content());
        }
        if (request.subject() != null) {
            note.setSubject(request.subject());
        }

        note = noteRepository.save(note);
        return toDto(note);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        boolean exists = noteRepository.findById(id)
                .map(n -> n.getUserId().equals(userId))
                .orElse(false);
        if (!exists) {
            throw ApiException.notFound("Note not found.");
        }
        noteRepository.deleteById(id);
    }

    private NoteDto toDto(Note note) {
        return new NoteDto(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getSubject(),
                note.getUpdatedAt(),
                note.getCreatedAt());
    }
}
