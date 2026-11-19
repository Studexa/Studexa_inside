package com.studexa.library;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.studexa.common.ApiException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class WikisourceReadings {
    private static final String WIKISOURCE = "https://en.wikisource.org";
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final List<Edition> EDITIONS = List.of(
            darwin("origin-introduction", "Introduction", 9427101, null, "origin-chapter-1"),
            history("declaration", "United States Declaration of Independence", "Continental Congress", "Engrossed copy", 16143344),
            austen("pride-chapter-1", "Chapter 1", 13055721, null, "pride-chapter-2"),
            darwin("origin-chapter-1", "Chapter I", 9427103, "origin-introduction", "origin-chapter-2"),
            history("magna-carta", "Magna Carta", "Ernest Flagg Henderson (translator)", "Select Historical Documents of the Middle Ages", 6391248),
            austen("pride-chapter-2", "Chapter 2", 13055726, "pride-chapter-1", "pride-chapter-3"),
            darwin("origin-chapter-2", "Chapter II", 9427105, "origin-chapter-1", null),
            history("common-sense-introduction", "Common Sense", "Thomas Paine", "Introduction (1776)", 11907289),
            austen("pride-chapter-3", "Chapter 3", 13055728, "pride-chapter-2", null));

    private final RestClient client;
    private final ObjectMapper json;
    private final Map<String, Reading> cache = new ConcurrentHashMap<>();

    public WikisourceReadings(ObjectMapper json) {
        this.json = json;
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(4));
        requests.setReadTimeout(Duration.ofSeconds(7));
        client = RestClient.builder().baseUrl(WIKISOURCE).requestFactory(requests)
                .defaultHeader("User-Agent", "Studexa/1.0 (https://github.com/GiO234765)").build();
    }

    public enum Subject { SCIENCE, HISTORY, LITERATURE }

    public record Edition(String id, String title, String author, String section, Subject subject, long revision,
                          String sourceUrl, String previousId, String nextId) {}

    public record Block(boolean heading, String text) {}

    public record Reading(Edition edition, List<Block> blocks) {}

    public List<Edition> list() { return EDITIONS; }

    public Reading get(String id) {
        Edition edition = EDITIONS.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Reading not found."));
        return cache.computeIfAbsent(id, ignored -> new Reading(edition, fetch(edition.revision())));
    }

    private List<Block> fetch(long revision) {
        try {
            List<Block> blocks = client.get().uri(builder -> builder.path("/w/api.php")
                            .queryParam("action", "parse").queryParam("format", "json").queryParam("formatversion", 2)
                            .queryParam("prop", "revid|text").queryParam("oldid", revision).build())
                    .accept(MediaType.APPLICATION_JSON).exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) throw new IOException("Reading request failed");
                        byte[] body = response.getBody().readNBytes(MAX_BODY + 1);
                        if (body.length > MAX_BODY) throw new IOException("Reading response too large");
                        var parsed = json.readTree(body).path("parse");
                        if (parsed.path("revid").asLong() != revision) throw new IOException("Reading revision did not match");
                        return parseBlocks(parsed.path("text").asText());
                    });
            if (blocks == null || blocks.isEmpty()) throw new RestClientException("Reading has no accessible text");
            return blocks;
        } catch (RestClientException | JacksonException ex) {
            throw ApiException.serviceUnavailable("READING_UNAVAILABLE", "This reading could not be loaded.");
        }
    }

    static List<Block> parseBlocks(String html) {
        Element content = Jsoup.parseBodyFragment(html).selectFirst(".prp-pages-output");
        if (content == null) return List.of();
        content.select(".ws-noexport, .pagenum, .pagenumber, .reference, .references, .mw-editsection, .noprint, script, style, table, nav").remove();
        return content.select("p, h1, h2, h3, h4, h5, h6, li").stream()
                .filter(block -> !block.tagName().equals("li") || block.select("p, li").size() == 1)
                .map(block -> new Block(block.tagName().startsWith("h"), block.text().replace("\u200B", "").strip()))
                .filter(block -> !block.text().isBlank()).toList();
    }

    private static Edition darwin(String id, String section, long revision, String previous, String next) {
        return edition(id, "On the Origin of Species", "Charles Darwin", section, Subject.SCIENCE, revision, previous, next);
    }

    private static Edition austen(String id, String section, long revision, String previous, String next) {
        return edition(id, "Pride and Prejudice", "Jane Austen", section, Subject.LITERATURE, revision, previous, next);
    }

    private static Edition history(String id, String title, String author, String section, long revision) {
        return edition(id, title, author, section, Subject.HISTORY, revision, null, null);
    }

    private static Edition edition(String id, String title, String author, String section, Subject subject, long revision,
                                   String previous, String next) {
        return new Edition(id, title, author, section, subject, revision, WIKISOURCE + "/w/index.php?oldid=" + revision, previous, next);
    }
}
