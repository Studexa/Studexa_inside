package com.studexa.notes;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.studexa.common.ApiException;

@Service
public class NoteAttachmentService {

    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ACCOUNT_BYTES = 50 * 1024 * 1024;
    private static final int MAX_PER_NOTE = 5;
    private static final String OWNED_ATTACHMENT = """
            from note_attachments a join notes n on n.id = a.note_id
            where a.note_id = :note and a.id = :attachment and n.user_id = :owner
            """;
    private static final String METADATA = """
            select id, filename, size_bytes, created_at, text_truncated, extracted_text <> '' as has_text
            from note_attachments
            """;

    private final JdbcClient jdbc;
    private final NoteRepository notes;
    private final TransactionTemplate transactions;

    public NoteAttachmentService(JdbcClient jdbc, NoteRepository notes, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.notes = notes;
        this.transactions = transactions;
    }

    public record Attachment(UUID id, String filename, int sizeBytes, Instant createdAt,
                             boolean textTruncated, boolean hasText) {}

    public record TextContent(String text) {}

    record Download(String filename, byte[] bytes) {}

    public List<Attachment> list(UUID owner, UUID note) {
        requireOwnedNote(owner, note);
        return jdbc.sql(METADATA + " where note_id = :note order by created_at, id")
                .param("note", note).query((rs, row) -> metadata(rs)).list();
    }

    public Attachment upload(UUID owner, UUID note, MultipartFile file) {
        requireOwnedNote(owner, note);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Choose a file that is not empty.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Files must be 5 MB or smaller.");
        }
        String filename = filename(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("INVALID_FILE", "The file upload could not be read.");
        }
        var extracted = NoteFileExtractor.extract(filename, bytes);
        return transactions.execute(status -> {
            // Serialize account uploads so simultaneous requests cannot exceed the storage limits.
            jdbc.sql("select id from users where id = :owner for no key update").param("owner", owner)
                    .query(UUID.class).optional().orElseThrow(NoteAttachmentService::noteNotFound);
            jdbc.sql("select id from notes where id = :note and user_id = :owner for key share")
                    .param("note", note).param("owner", owner).query(UUID.class).optional()
                    .orElseThrow(NoteAttachmentService::noteNotFound);
            long count = jdbc.sql("select count(*) from note_attachments where note_id = :note")
                    .param("note", note).query(Long.class).single();
            if (count >= MAX_PER_NOTE) {
                throw ApiException.conflict("NOTE_FILE_LIMIT", "A note can hold up to five files.");
            }
            long used = jdbc.sql("""
                    select coalesce(sum(a.size_bytes), 0) from note_attachments a
                    join notes n on n.id = a.note_id where n.user_id = :owner
                    """).param("owner", owner).query(Long.class).single();
            if (used + bytes.length > MAX_ACCOUNT_BYTES) {
                throw ApiException.conflict("ACCOUNT_FILE_LIMIT", "Your attachment storage is full. Remove a file and try again.");
            }
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                    insert into note_attachments (id, note_id, filename, size_bytes, file_bytes, extracted_text, text_truncated)
                    values (:id, :note, :filename, :size, :bytes, :text, :truncated)
                    """).param("id", id).param("note", note).param("filename", filename).param("size", bytes.length)
                    .param("bytes", bytes).param("text", extracted.text()).param("truncated", extracted.truncated()).update();
            return jdbc.sql(METADATA + " where id = :id").param("id", id).query((rs, row) -> metadata(rs)).single();
        });
    }

    public TextContent text(UUID owner, UUID note, UUID attachment) {
        String text = owned("select a.extracted_text " + OWNED_ATTACHMENT, owner, note, attachment)
                .query(String.class).optional().orElseThrow(NoteAttachmentService::attachmentNotFound);
        return new TextContent(text);
    }

    Download download(UUID owner, UUID note, UUID attachment) {
        return owned("select a.filename, a.file_bytes " + OWNED_ATTACHMENT, owner, note, attachment)
                .query((rs, row) -> new Download(rs.getString("filename"), rs.getBytes("file_bytes")))
                .optional().orElseThrow(NoteAttachmentService::attachmentNotFound);
    }

    public void remove(UUID owner, UUID note, UUID attachment) {
        int deleted = owned("delete from note_attachments where id in (select a.id " + OWNED_ATTACHMENT + ")",
                owner, note, attachment).update();
        if (deleted == 0) throw attachmentNotFound();
    }

    private JdbcClient.StatementSpec owned(String sql, UUID owner, UUID note, UUID attachment) {
        return jdbc.sql(sql).param("owner", owner).param("note", note).param("attachment", attachment);
    }

    private void requireOwnedNote(UUID owner, UUID note) {
        if (!notes.existsByIdAndUserId(note, owner)) throw noteNotFound();
    }

    private static Attachment metadata(ResultSet rs) throws SQLException {
        return new Attachment(rs.getObject("id", UUID.class), rs.getString("filename"), rs.getInt("size_bytes"),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("text_truncated"), rs.getBoolean("has_text"));
    }

    private static String filename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        if (name.length() > 255 || name.lastIndexOf('.') <= 0) {
            throw ApiException.badRequest("UNSUPPORTED_FILE", "Choose a PDF, DOCX, TXT, or Markdown file.");
        }
        return name;
    }

    private static ApiException noteNotFound() {
        return ApiException.notFound("Note not found.");
    }

    private static ApiException attachmentNotFound() {
        return ApiException.notFound("Attachment not found.");
    }
}
