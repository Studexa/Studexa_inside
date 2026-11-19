package com.studexa.notes;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {"app.beta.enabled=false", "app.security.auth-rate-limit.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoteAttachmentIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;

    @Test
    void storesAllFormatsAndKeepsOriginalsAndExtractedTextPrivate() throws Exception {
        String owner = register();
        String other = register();
        String base = noteAttachments(owner);
        byte[] text = "វដ្តទឹក\nWater vapor cools.".getBytes(UTF_8);
        String txt = base + "/" + read(upload(owner, base, "study.txt", text)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.hasText").value(true)), "$.id");
        upload(owner, base, "study.md", text).andExpect(status().isCreated());
        String pdf = base + "/" + read(upload(owner, base, "study.pdf", pdf("Clouds form as water cools."))
                .andExpect(status().isCreated()), "$.id");
        String word = base + "/" + read(upload(owner, base, "study.docx", word("Water evaporates."))
                .andExpect(status().isCreated()), "$.id");
        upload(owner, base, "scan.pdf", pdf(null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasText").value(false));
        upload(owner, base, "sixth.txt", text).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTE_FILE_LIMIT"));

        mvc.perform(get(base).header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[0].fileBytes").doesNotExist());
        mvc.perform(get(txt + "/text").header("Authorization", owner))
                .andExpect(jsonPath("$.text").value(new String(text, UTF_8)));
        mvc.perform(get(pdf + "/text").header("Authorization", owner))
                .andExpect(jsonPath("$.text").value("Clouds form as water cools."));
        mvc.perform(get(word + "/text").header("Authorization", owner))
                .andExpect(jsonPath("$.text").value("Water evaporates."));
        mvc.perform(get(txt + "/download").header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(text));

        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).header("Authorization", other)).andExpect(status().isNotFound());
        upload(other, base, "study.txt", text).andExpect(status().isNotFound());
        for (String endpoint : new String[] {"/text", "/download"}) {
            mvc.perform(get(txt + endpoint).header("Authorization", other)).andExpect(status().isNotFound());
        }
        mvc.perform(delete(txt).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(delete(txt).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get(txt + "/download").header("Authorization", owner)).andExpect(status().isNotFound());

        String notePath = base.substring(0, base.lastIndexOf('/'));
        mvc.perform(delete(notePath).header("Authorization", owner)).andExpect(status().isNoContent());
        UUID noteId = UUID.fromString(notePath.substring(notePath.lastIndexOf('/') + 1));
        assertThat(jdbc.sql("select count(*) from note_attachments where note_id = :id")
                .param("id", noteId).query(Long.class).single()).isZero();
    }

    @Test
    void rejectsUnsupportedCorruptEmptyAndOversizedUploadsWithoutCreatingAttachments() throws Exception {
        String owner = register();
        String base = noteAttachments(owner);
        byte[] text = "Not a real PDF or Word document".getBytes(UTF_8);
        upload(owner, base, "corrupt.pdf", text).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        upload(owner, base, "corrupt.docx", text).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        upload(owner, base, "unsupported.png", text).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"));
        upload(owner, base, "empty.txt", new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        upload(owner, base, "oversized.txt", new byte[5 * 1024 * 1024 + 1]).andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(get(base).header("Authorization", owner)).andExpect(jsonPath("$.length()").value(0));
    }

    private String register() throws Exception {
        return "Bearer " + read(mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
                {"email":"%s@example.test","password":"test-password1","nickname":"Dara"}
                """.formatted(UUID.randomUUID()))).andExpect(status().isCreated()), "$.accessToken");
    }

    private String noteAttachments(String auth) throws Exception {
        String id = read(mvc.perform(post("/api/v1/notes").header("Authorization", auth)
                .contentType("application/json").content("{\"title\":\"Study notes\"}"))
                .andExpect(status().isCreated()), "$.id");
        return "/api/v1/notes/" + id + "/attachments";
    }

    private ResultActions upload(String auth, String path, String filename, byte[] bytes) throws Exception {
        var file = new MockMultipartFile("file", filename, "application/octet-stream", bytes);
        return mvc.perform(multipart(path).file(file).header("Authorization", auth));
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(UTF_8), path);
    }

    private static byte[] word(String text) throws Exception {
        try (var word = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            word.createParagraph().createRun().setText(text);
            word.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdf(String text) throws Exception {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            try (var stream = new PDPageContentStream(pdf, page)) {
                if (text == null) {
                    var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
                    stream.drawImage(LosslessFactory.createFromImage(pdf, image), 35, 720);
                } else {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(35, 720);
                    stream.showText(text);
                    stream.endText();
                }
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
