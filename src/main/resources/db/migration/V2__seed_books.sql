-- Demo catalogue: e-books (ids 1-12) and journal issues (ids 13-15).
-- Book 1 has a single licence (easy to see "no copies left"), book 12 is off the shelf.
INSERT INTO book (format, title, isbn, author, category, description, total_copies, available_copies, status, created_at, updated_at)
VALUES ('EBOOK', 'Clean Code', '9780132350884', 'Robert C. Martin', 'Software',
        'A handbook of agile software craftsmanship.', 1, 1, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Domain-Driven Design', '9780321125217', 'Eric Evans', 'Software',
        'Tackling complexity in the heart of software.', 3, 3, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Designing Data-Intensive Applications', '9781449373320', 'Martin Kleppmann', 'Software',
        'The big ideas behind reliable, scalable, and maintainable systems.', 5, 5, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Effective Java', '9780134685991', 'Joshua Bloch', 'Software',
        'Best practices for the Java platform.', 4, 4, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Java Concurrency in Practice', '9780321349606', 'Brian Goetz', 'Software',
        'Writing correct and efficient concurrent Java programs.', 2, 2, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Design Patterns', '9780201633610', 'Erich Gamma', 'Software',
        'Elements of reusable object-oriented software.', 3, 3, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Pride and Prejudice', '9780141439518', 'Jane Austen', 'Fiction',
        'A novel of manners.', 10, 10, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', '1984', '9780451524935', 'George Orwell', 'Fiction',
        'A dystopian social science fiction novel.', 6, 6, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'To Kill a Mockingbird', '9780061120084', 'Harper Lee', 'Fiction',
        'A novel about justice and growing up in the American South.', 4, 4, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Sapiens', '9780062316097', 'Yuval Noah Harari', 'History',
        'A brief history of humankind.', 5, 5, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Thinking, Fast and Slow', '9780374533557', 'Daniel Kahneman', 'Psychology',
        'How two systems of thought shape our judgements.', 3, 3, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('EBOOK', 'Head First Design Patterns', '9780596007126', 'Eric Freeman', 'Software',
        'A brain-friendly guide. Licence expired, kept for existing readers.', 2, 2, 'OFF_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO book (format, title, issn, issue, published_on, category, description, total_copies, available_copies, status, created_at, updated_at)
VALUES ('JOURNAL', 'Communications of the ACM', '0001-0782', 'Vol. 67, No. 3', DATE '2024-03-01', 'Computing',
        'Monthly magazine of the Association for Computing Machinery.', 3, 3, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('JOURNAL', 'Communications of the ACM', '0001-0782', 'Vol. 67, No. 4', DATE '2024-04-01', 'Computing',
        'Monthly magazine of the Association for Computing Machinery.', 3, 3, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
       ('JOURNAL', 'Harvard Business Review', '0017-8012', 'March-April 2024', DATE '2024-03-01', 'Business',
        'Management ideas and research for business leaders.', 2, 2, 'ON_SHELF', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
