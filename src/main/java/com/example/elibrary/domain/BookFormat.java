package com.example.elibrary.domain;

/**
 * Kind of digital content in the catalogue. Both kinds are lent under exactly the same rules;
 * they differ only in how they are identified and described.
 */
public enum BookFormat {
    /** identified by ISBN, has an author */
    EBOOK,
    /** one issue of a journal: identified by ISSN + issue, has a publication date */
    JOURNAL
}
