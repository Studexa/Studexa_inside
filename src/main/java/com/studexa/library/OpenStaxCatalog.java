package com.studexa.library;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.studexa.common.ApiException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpenStaxCatalog {
    private static final URI ENDPOINT = URI.create("https://openstax.org/apps/cms/api/books/");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Duration CACHE_TIME = Duration.ofHours(1);
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(30);

    private final RestClient client;
    private final ObjectMapper json;
    private volatile Cached cached = new Cached(null, Instant.EPOCH);

    public OpenStaxCatalog(ObjectMapper json) {
        this.json = json;
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(4));
        requests.setReadTimeout(Duration.ofSeconds(7));
        client = RestClient.builder().requestFactory(requests).build();
    }

    public enum Subject { SCIENCE, HISTORY, MATH, COMPUTING, HUMANITIES, SOCIAL_SCIENCES, BUSINESS, HEALTH, COLLEGE_SUCCESS }

    public record Book(String slug, String title, Set<Subject> subjects, String readerUrl, String publisherUrl) {}

    public record Catalog(List<Book> books, boolean stale) {}

    private record Cached(Catalog catalog, Instant expiresAt) {}

    public Catalog get() {
        Cached snapshot = cached;
        return Instant.now().isBefore(snapshot.expiresAt()) ? available(snapshot.catalog()) : refresh();
    }

    private synchronized Catalog refresh() {
        if (Instant.now().isBefore(cached.expiresAt())) return available(cached.catalog());
        try {
            List<Book> books = client.get().uri(ENDPOINT).accept(MediaType.APPLICATION_JSON).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) throw new IOException("OpenStax returned an error");
                byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) throw new IOException("OpenStax catalog exceeds the response limit");
                return parse(json.readTree(body));
            });
            if (books == null || books.isEmpty()) throw new RestClientException("No readable OpenStax books returned");
            cached = new Cached(new Catalog(books, false), Instant.now().plus(CACHE_TIME));
        } catch (RestClientException | JacksonException ex) {
            Catalog previous = cached.catalog();
            cached = new Cached(previous == null ? null : new Catalog(previous.books(), true), Instant.now().plus(FAILURE_BACKOFF));
        }
        return available(cached.catalog());
    }

    private static Catalog available(Catalog catalog) {
        if (catalog == null) throw ApiException.serviceUnavailable("LIBRARY_UNAVAILABLE", "The book library is temporarily unavailable.");
        return catalog;
    }

    static List<Book> parse(JsonNode root) {
        List<Book> books = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        for (JsonNode item : root.path("books")) {
            if (!"live".equals(item.path("book_state").asText())) continue;
            String slug = item.path("slug").asText();
            String title = item.path("title").asText().strip();
            if (!slug.matches("books/[a-z0-9-]+") || title.isEmpty()) continue;
            String reader = item.path("webview_rex_link").asText();
            String expectedPrefix = "https://openstax.org/" + slug + "/pages/";
            if (!reader.startsWith(expectedPrefix) || !reader.substring(expectedPrefix.length()).matches("[a-zA-Z0-9%._-]+")) continue;
            if (!slugs.add(slug)) continue;
            Set<Subject> subjects = EnumSet.noneOf(Subject.class);
            for (JsonNode subject : item.path("subjects")) {
                switch (subject.asText()) {
                    case "Science" -> subjects.add(Subject.SCIENCE);
                    case "Math" -> subjects.add(Subject.MATH);
                    case "Computer Science" -> subjects.add(Subject.COMPUTING);
                    case "Humanities" -> subjects.add(Subject.HUMANITIES);
                    case "Social Sciences" -> subjects.add(Subject.SOCIAL_SCIENCES);
                    case "Business" -> subjects.add(Subject.BUSINESS);
                    case "Nursing" -> subjects.add(Subject.HEALTH);
                    case "College Success" -> subjects.add(Subject.COLLEGE_SUCCESS);
                    default -> { }
                }
            }
            for (JsonNode category : item.path("subject_categories")) {
                if (category.asText().toLowerCase(Locale.ROOT).contains("history")) subjects.add(Subject.HISTORY);
            }
            books.add(new Book(slug.substring("books/".length()), title, Collections.unmodifiableSet(subjects), reader, "https://openstax.org/" + slug));
        }
        return books.stream().sorted(Comparator.comparing(Book::title, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
