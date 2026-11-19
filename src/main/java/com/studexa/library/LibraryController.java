package com.studexa.library;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {
    private final OpenStaxCatalog openStax;

    public LibraryController(OpenStaxCatalog openStax) {
        this.openStax = openStax;
    }

    @GetMapping("/books")
    public OpenStaxCatalog.Catalog listLibraryBooks() {
        return openStax.get();
    }
}
