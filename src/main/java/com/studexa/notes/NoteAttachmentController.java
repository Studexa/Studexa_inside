package com.studexa.notes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.studexa.common.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/notes/{noteId}/attachments")
public class NoteAttachmentController {

    private final NoteAttachmentService attachments;

    public NoteAttachmentController(NoteAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping
    @Operation(operationId = "listNoteAttachments")
    public List<NoteAttachmentService.Attachment> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noteId) {
        return attachments.list(CurrentUser.id(jwt), noteId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadNoteAttachment")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteAttachmentService.Attachment upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noteId,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return attachments.upload(CurrentUser.id(jwt), noteId, file);
    }

    @GetMapping("/{attachmentId}/text")
    @Operation(operationId = "getNoteAttachmentText")
    public ResponseEntity<NoteAttachmentService.TextContent> text(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID noteId, @PathVariable UUID attachmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(attachments.text(CurrentUser.id(jwt), noteId, attachmentId));
    }

    @GetMapping(value = "/{attachmentId}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(operationId = "downloadNoteAttachment")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noteId,
            @PathVariable UUID attachmentId) {
        var file = attachments.download(CurrentUser.id(jwt), noteId, attachmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(operationId = "removeNoteAttachment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID noteId, @PathVariable UUID attachmentId) {
        attachments.remove(CurrentUser.id(jwt), noteId, attachmentId);
    }
}
