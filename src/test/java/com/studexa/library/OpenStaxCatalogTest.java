package com.studexa.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class OpenStaxCatalogTest {
    @Test
    void groupsHistoryAndScienceAndKeepsOnlyOfficialReadableEditions() {
        var catalog = JsonMapper.builder().build().readTree("""
                {"books":[
                  {"slug":"books/world-history","title":"World History","book_state":"live",
                   "subjects":["Humanities"],"subject_categories":["World History"],
                   "webview_rex_link":"https://openstax.org/books/world-history/pages/1-introduction"},
                  {"slug":"books/biology","title":"Biology","book_state":"live","subjects":["Science"],
                   "webview_rex_link":"https://openstax.org/books/biology/pages/1-introduction"},
                  {"slug":"books/not-released","title":"Not released","book_state":"coming_soon",
                   "webview_rex_link":"https://openstax.org/books/not-released/pages/1-introduction"},
                  {"slug":"books/invalid-link","title":"Invalid link","book_state":"live",
                   "webview_rex_link":"https://openstax.org.example/books/invalid-link/pages/1-introduction"}
                ]}
                """);

        var books = OpenStaxCatalog.parse(catalog);

        assertThat(books).extracting(OpenStaxCatalog.Book::title).containsExactly("Biology", "World History");
        assertThat(books.getFirst().subjects()).containsExactly(OpenStaxCatalog.Subject.SCIENCE);
        assertThat(books.getLast().subjects()).containsExactlyInAnyOrder(OpenStaxCatalog.Subject.HISTORY, OpenStaxCatalog.Subject.HUMANITIES);
        assertThat(books.getLast().publisherUrl()).isEqualTo("https://openstax.org/books/world-history");
    }
}
