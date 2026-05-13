-- ============================================================================
-- bookdata_4991.sql
-- Repeatable schema + loading script for Phase I
-- MySQL 8.x / InnoDB / utf8mb4
--
-- Expected clean files produced by ETL.java in MySQL Uploads folder:
--   Authors_Clean.csv
--   Conferences_Clean.csv
--   Journals_Clean.csv
--   Conference_Articles_Clean.csv
--   Journal_Articles_Clean.csv
--   Conference_Article_Authors_Clean.csv
--   Journal_Article_Authors_Clean.csv
-- Optional:
--   Rejected_Rows.csv
--
-- IMPORTANT:
-- 1) The factual article tables are loaded BEFORE the N:M junction tables.
-- 2) The article_id in the junction files must be the internal article_id produced
--    by the ETL, NOT the original DBLP id.
-- 3) This script assumes tab-separated clean files with no header row.
--    If your generated files contain headers, add: IGNORE 1 LINES
--    to each LOAD DATA statement.
-- ============================================================================

DROP DATABASE IF EXISTS bookdata_4991;

CREATE DATABASE bookdata_4991
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE bookdata_4991;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- =========================================================================
-- 1. LOOKUP TABLES
-- =========================================================================

CREATE TABLE Authors (
    author_id INT NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (author_id),
    UNIQUE KEY uq_authors_name (author_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE Conferences (
    conf_id INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    acronym VARCHAR(100),
    source_name VARCHAR(100),
    rank_category VARCHAR(20),
    dblp_flag VARCHAR(20),
    primary_for VARCHAR(50),
    PRIMARY KEY (conf_id),
    UNIQUE KEY uq_conferences_title_acronym (title, acronym),
    KEY idx_conferences_acronym (acronym),
    KEY idx_conferences_rank (rank_category),
    KEY idx_conferences_primary_for (primary_for)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE Journals (
    journal_id INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    oa VARCHAR(20),
    country VARCHAR(150),
    sjr_index DECIMAL(12,3),
    cite_score DECIMAL(12,3),
    h_index INT,
    best_quartile VARCHAR(10),
    best_subject_area VARCHAR(255),
    total_docs INT,
    total_docs_3y INT,
    total_refs INT,
    total_cites_3y INT,
    citable_docs_3y INT,
    cites_per_doc_2y DECIMAL(12,3),
    refs_per_doc DECIMAL(12,3),
    PRIMARY KEY (journal_id),
    UNIQUE KEY uq_journals_title (title),
    KEY idx_journals_quartile (best_quartile),
    KEY idx_journals_subject_area (best_subject_area),
    KEY idx_journals_country (country)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================================
-- 2. FACTUAL TABLES
-- =========================================================================

CREATE TABLE Conference_Articles (
    article_id INT NOT NULL,
    original_dblp_id VARCHAR(100) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    year INT NOT NULL,
    pages VARCHAR(100),
    url VARCHAR(1000),
    conf_id INT NOT NULL,
    PRIMARY KEY (article_id),
    UNIQUE KEY uq_conf_articles_original_dblp_id (original_dblp_id),
    KEY idx_conf_articles_year (year),
    KEY idx_conf_articles_conf_year (conf_id, year),
    CONSTRAINT fk_conf_articles_conference
        FOREIGN KEY (conf_id) REFERENCES Conferences(conf_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_conf_articles_year
        CHECK (year BETWEEN 1900 AND 2100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE Journal_Articles (
    article_id INT NOT NULL,
    original_dblp_id VARCHAR(100) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    year INT NOT NULL,
    volume VARCHAR(100),
    number VARCHAR(100),
    pages VARCHAR(100),
    url VARCHAR(1000),
    journal_id INT NOT NULL,
    PRIMARY KEY (article_id),
    UNIQUE KEY uq_journal_articles_original_dblp_id (original_dblp_id),
    KEY idx_journal_articles_year (year),
    KEY idx_journal_articles_journal_year (journal_id, year),
    CONSTRAINT fk_journal_articles_journal
        FOREIGN KEY (journal_id) REFERENCES Journals(journal_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_journal_articles_year
        CHECK (year BETWEEN 1900 AND 2100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================================
-- 3. N:M JUNCTION TABLES
-- =========================================================================

CREATE TABLE Conference_Article_Authors (
    article_id INT NOT NULL,
    author_id INT NOT NULL,
    PRIMARY KEY (article_id, author_id),
    KEY idx_conf_article_authors_author (author_id),
    CONSTRAINT fk_conf_article_authors_article
        FOREIGN KEY (article_id) REFERENCES Conference_Articles(article_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_conf_article_authors_author
        FOREIGN KEY (author_id) REFERENCES Authors(author_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE Journal_Article_Authors (
    article_id INT NOT NULL,
    author_id INT NOT NULL,
    PRIMARY KEY (article_id, author_id),
    KEY idx_journal_article_authors_author (author_id),
    CONSTRAINT fk_journal_article_authors_article
        FOREIGN KEY (article_id) REFERENCES Journal_Articles(article_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_journal_article_authors_author
        FOREIGN KEY (author_id) REFERENCES Authors(author_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Optional table for ETL rejects / invalid rows.
CREATE TABLE Rejected_Rows (
    rejected_id INT AUTO_INCREMENT PRIMARY KEY,
    source_file VARCHAR(255),
    original_line_number INT,
    reason VARCHAR(500),
    raw_row TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================================
-- 4. LOAD DATA
-- =========================================================================
-- Change this path if your MySQL secure_file_priv directory is different.
-- Check it with: SHOW VARIABLES LIKE 'secure_file_priv';

LOAD DATA LOCAL INFILE './Authors_Clean.csv'
INTO TABLE Authors
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(author_id, author_name);

LOAD DATA LOCAL INFILE './Conferences_Clean.csv'
INTO TABLE Conferences
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(conf_id, title, acronym, source_name, rank_category, dblp_flag, primary_for);

LOAD DATA LOCAL INFILE './Journals_Clean.csv'
INTO TABLE Journals
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(@journal_id, @title, @oa, @country, @sjr_index, @cite_score, @h_index, @best_quartile,
 @best_subject_area, @total_docs, @total_docs_3y, @total_refs, @total_cites_3y,
 @citable_docs_3y, @cites_per_doc_2y, @refs_per_doc)
SET
    journal_id = CAST(NULLIF(@journal_id, '') AS UNSIGNED),
    title = NULLIF(@title, ''),
    oa = NULLIF(@oa, ''),
    country = NULLIF(@country, ''),
    sjr_index = CAST(NULLIF(REPLACE(@sjr_index, ',', ''), '') AS DECIMAL(12,3)),
    cite_score = CAST(NULLIF(REPLACE(@cite_score, ',', ''), '') AS DECIMAL(12,3)),
    h_index = CAST(NULLIF(REPLACE(@h_index, ',', ''), '') AS UNSIGNED),
    best_quartile = NULLIF(@best_quartile, ''),
    best_subject_area = NULLIF(@best_subject_area, ''),
    total_docs = CAST(NULLIF(REPLACE(@total_docs, ',', ''), '') AS UNSIGNED),
    total_docs_3y = CAST(NULLIF(REPLACE(@total_docs_3y, ',', ''), '') AS UNSIGNED),
    total_refs = CAST(NULLIF(REPLACE(@total_refs, ',', ''), '') AS UNSIGNED),
    total_cites_3y = CAST(NULLIF(REPLACE(@total_cites_3y, ',', ''), '') AS UNSIGNED),
    citable_docs_3y = CAST(NULLIF(REPLACE(@citable_docs_3y, ',', ''), '') AS UNSIGNED),
    cites_per_doc_2y = CAST(NULLIF(REPLACE(@cites_per_doc_2y, ',', ''), '') AS DECIMAL(12,3)),
    refs_per_doc = CAST(NULLIF(REPLACE(@refs_per_doc, ',', ''), '') AS DECIMAL(12,3));

LOAD DATA LOCAL INFILE './Conference_Articles_Clean.csv'
INTO TABLE Conference_Articles
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(article_id, original_dblp_id, title, year, pages, conf_id);

LOAD DATA LOCAL INFILE './Journal_Articles_Clean.csv'
INTO TABLE Journal_Articles
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(article_id, original_dblp_id, title, year, pages, url, journal_id);

LOAD DATA LOCAL INFILE './Conference_Article_Authors_Clean.csv'
INTO TABLE Conference_Article_Authors
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(article_id, author_id);

LOAD DATA LOCAL INFILE './Journal_Article_Authors_Clean.csv'
INTO TABLE Journal_Article_Authors
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(article_id, author_id);

-- Optional: enable only if ETL.java produced Rejected_Rows.csv with these 4 columns.
-- LOAD DATA LOCAL INFILE './Rejected_Rows.csv'
-- INTO TABLE Rejected_Rows
-- CHARACTER SET utf8mb4
-- FIELDS TERMINATED BY '\t'
-- LINES TERMINATED BY '\n'
-- (source_file, original_line_number, reason, raw_row);

-- =========================================================================
-- 5. SANITY CHECKS
-- =========================================================================

SELECT 'Authors' AS table_name, COUNT(*) AS rows_loaded FROM Authors
UNION ALL SELECT 'Conferences', COUNT(*) FROM Conferences
UNION ALL SELECT 'Journals', COUNT(*) FROM Journals
UNION ALL SELECT 'Conference_Articles', COUNT(*) FROM Conference_Articles
UNION ALL SELECT 'Journal_Articles', COUNT(*) FROM Journal_Articles
UNION ALL SELECT 'Conference_Article_Authors', COUNT(*) FROM Conference_Article_Authors
UNION ALL SELECT 'Journal_Article_Authors', COUNT(*) FROM Journal_Article_Authors;

-- These should both return 0.
SELECT COUNT(*) AS orphan_conference_article_author_rows
FROM Conference_Article_Authors caa
LEFT JOIN Conference_Articles ca ON ca.article_id = caa.article_id
LEFT JOIN Authors a ON a.author_id = caa.author_id
WHERE ca.article_id IS NULL OR a.author_id IS NULL;

SELECT COUNT(*) AS orphan_journal_article_author_rows
FROM Journal_Article_Authors jaa
LEFT JOIN Journal_Articles ja ON ja.article_id = jaa.article_id
LEFT JOIN Authors a ON a.author_id = jaa.author_id
WHERE ja.article_id IS NULL OR a.author_id IS NULL;

-- =========================================================================
-- 6. FIRST-CUT VIEWS FOR PHASE I / PHASE II QUERIES
-- =========================================================================

CREATE OR REPLACE VIEW v_publications_unified AS
SELECT
    'CONFERENCE' AS publication_type,
    ca.article_id,
    ca.original_dblp_id,
    ca.title AS article_title,
    ca.year,
    ca.pages,
    ca.url,
    c.conf_id AS venue_id,
    c.title AS venue_title,
    c.acronym AS venue_acronym,
    c.rank_category AS venue_rank,
    c.primary_for AS venue_category
FROM Conference_Articles ca
JOIN Conferences c ON c.conf_id = ca.conf_id
UNION ALL
SELECT
    'JOURNAL' AS publication_type,
    ja.article_id,
    ja.original_dblp_id,
    ja.title AS article_title,
    ja.year,
    ja.pages,
    ja.url,
    j.journal_id AS venue_id,
    j.title AS venue_title,
    NULL AS venue_acronym,
    j.best_quartile AS venue_rank,
    j.best_subject_area AS venue_category
FROM Journal_Articles ja
JOIN Journals j ON j.journal_id = ja.journal_id;

CREATE OR REPLACE VIEW v_article_authors_unified AS
SELECT
    'CONFERENCE' AS publication_type,
    ca.article_id,
    ca.original_dblp_id,
    ca.title AS article_title,
    ca.year,
    ca.conf_id AS venue_id,
    c.title AS venue_title,
    c.acronym AS venue_acronym,
    a.author_id,
    a.author_name
FROM Conference_Articles ca
JOIN Conferences c ON c.conf_id = ca.conf_id
JOIN Conference_Article_Authors caa ON caa.article_id = ca.article_id
JOIN Authors a ON a.author_id = caa.author_id
UNION ALL
SELECT
    'JOURNAL' AS publication_type,
    ja.article_id,
    ja.original_dblp_id,
    ja.title AS article_title,
    ja.year,
    ja.journal_id AS venue_id,
    j.title AS venue_title,
    NULL AS venue_acronym,
    a.author_id,
    a.author_name
FROM Journal_Articles ja
JOIN Journals j ON j.journal_id = ja.journal_id
JOIN Journal_Article_Authors jaa ON jaa.article_id = ja.article_id
JOIN Authors a ON a.author_id = jaa.author_id;

CREATE OR REPLACE VIEW v_conference_yearly_stats AS
SELECT
    c.conf_id,
    c.title AS conference_title,
    c.acronym,
    c.rank_category,
    c.primary_for,
    ca.year,
    COUNT(DISTINCT ca.article_id) AS total_articles,
    COUNT(caa.author_id) AS total_authorships,
    COUNT(DISTINCT caa.author_id) AS distinct_authors,
    ROUND(COUNT(caa.author_id) / NULLIF(COUNT(DISTINCT ca.article_id), 0), 2) AS avg_authors_per_article
FROM Conferences c
JOIN Conference_Articles ca ON ca.conf_id = c.conf_id
LEFT JOIN Conference_Article_Authors caa ON caa.article_id = ca.article_id
GROUP BY c.conf_id, c.title, c.acronym, c.rank_category, c.primary_for, ca.year;

CREATE OR REPLACE VIEW v_journal_yearly_stats AS
SELECT
    j.journal_id,
    j.title AS journal_title,
    j.best_quartile,
    j.best_subject_area,
    ja.year,
    COUNT(DISTINCT ja.article_id) AS total_articles,
    COUNT(jaa.author_id) AS total_authorships,
    COUNT(DISTINCT jaa.author_id) AS distinct_authors,
    ROUND(COUNT(jaa.author_id) / NULLIF(COUNT(DISTINCT ja.article_id), 0), 2) AS avg_authors_per_article
FROM Journals j
JOIN Journal_Articles ja ON ja.journal_id = j.journal_id
LEFT JOIN Journal_Article_Authors jaa ON jaa.article_id = ja.article_id
GROUP BY j.journal_id, j.title, j.best_quartile, j.best_subject_area, ja.year;

CREATE OR REPLACE VIEW v_conference_profile AS
SELECT
    c.conf_id,
    c.title AS conference_title,
    c.acronym,
    c.rank_category,
    c.primary_for,
    MIN(ca.year) AS first_year,
    MAX(ca.year) AS last_year,
    COUNT(DISTINCT ca.article_id) AS total_articles,
    COUNT(caa.author_id) AS total_authorships,
    COUNT(DISTINCT caa.author_id) AS distinct_authors,
    ROUND(COUNT(caa.author_id) / NULLIF(COUNT(DISTINCT ca.article_id), 0), 2) AS avg_authors_per_article,
    ROUND(COUNT(DISTINCT ca.article_id) / NULLIF(COUNT(DISTINCT ca.year), 0), 2) AS avg_articles_per_year
FROM Conferences c
JOIN Conference_Articles ca ON ca.conf_id = c.conf_id
LEFT JOIN Conference_Article_Authors caa ON caa.article_id = ca.article_id
GROUP BY c.conf_id, c.title, c.acronym, c.rank_category, c.primary_for;

CREATE OR REPLACE VIEW v_journal_profile AS
SELECT
    j.journal_id,
    j.title AS journal_title,
    j.best_quartile,
    j.best_subject_area,
    j.country,
    j.sjr_index,
    j.cite_score,
    j.h_index,
    MIN(ja.year) AS first_year,
    MAX(ja.year) AS last_year,
    COUNT(DISTINCT ja.article_id) AS total_articles,
    COUNT(jaa.author_id) AS total_authorships,
    COUNT(DISTINCT jaa.author_id) AS distinct_authors,
    ROUND(COUNT(jaa.author_id) / NULLIF(COUNT(DISTINCT ja.article_id), 0), 2) AS avg_authors_per_article,
    ROUND(COUNT(DISTINCT ja.article_id) / NULLIF(COUNT(DISTINCT ja.year), 0), 2) AS avg_articles_per_year
FROM Journals j
JOIN Journal_Articles ja ON ja.journal_id = j.journal_id
LEFT JOIN Journal_Article_Authors jaa ON jaa.article_id = ja.article_id
GROUP BY j.journal_id, j.title, j.best_quartile, j.best_subject_area, j.country, j.sjr_index, j.cite_score, j.h_index;

CREATE OR REPLACE VIEW v_author_yearly_stats AS
SELECT
    author_id,
    author_name,
    year,
    COUNT(DISTINCT CONCAT(publication_type, ':', article_id)) AS total_articles,
    SUM(publication_type = 'CONFERENCE') AS conference_articles,
    SUM(publication_type = 'JOURNAL') AS journal_articles
FROM v_article_authors_unified
GROUP BY author_id, author_name, year;

CREATE OR REPLACE VIEW v_author_profile AS
SELECT
    author_id,
    author_name,
    MIN(year) AS first_year,
    MAX(year) AS last_year,
    COUNT(DISTINCT CONCAT(publication_type, ':', article_id)) AS total_articles,
    SUM(publication_type = 'CONFERENCE') AS conference_articles,
    SUM(publication_type = 'JOURNAL') AS journal_articles,
    ROUND(COUNT(DISTINCT CONCAT(publication_type, ':', article_id)) / NULLIF(COUNT(DISTINCT year), 0), 2) AS avg_articles_per_year
FROM v_article_authors_unified
GROUP BY author_id, author_name;

CREATE OR REPLACE VIEW v_year_profile AS
SELECT
    year,
    COUNT(DISTINCT CONCAT(publication_type, ':', article_id)) AS total_articles,
    SUM(publication_type = 'CONFERENCE') AS conference_articles,
    SUM(publication_type = 'JOURNAL') AS journal_articles,
    COUNT(DISTINCT CASE WHEN publication_type = 'CONFERENCE' THEN venue_id END) AS distinct_conferences,
    COUNT(DISTINCT CASE WHEN publication_type = 'JOURNAL' THEN venue_id END) AS distinct_journals,
    COUNT(author_id) AS total_authorships,
    COUNT(DISTINCT author_id) AS distinct_authors
FROM v_article_authors_unified
GROUP BY year;

-- =========================================================================
-- 7. EXAMPLE QUERIES
-- =========================================================================

-- Conference profile by acronym:
-- SELECT * FROM v_conference_profile WHERE acronym = 'ICDE';

-- Conference yearly line-chart data:
-- SELECT year, total_articles, total_authorships, distinct_authors
-- FROM v_conference_yearly_stats
-- WHERE acronym = 'ICDE'
-- ORDER BY year;

-- Journal profile by title:
-- SELECT * FROM v_journal_profile WHERE journal_title LIKE '%Knowledge and Data Engineering%';

-- Author profile:
-- SELECT * FROM v_author_profile WHERE author_name LIKE '%Dik Lun Lee%';

-- Year profile:
-- SELECT * FROM v_year_profile WHERE year = 2012;

-- Bar chart: total articles per conference:
-- SELECT acronym, conference_title, total_articles, avg_articles_per_year, avg_authors_per_article
-- FROM v_conference_profile
-- ORDER BY total_articles DESC;

-- Scatter plot: journal ranking metrics:
-- SELECT title, total_docs_3y, total_refs, total_cites_3y, citable_docs_3y,
--        cites_per_doc_2y, refs_per_doc
-- FROM Journals
-- WHERE total_docs_3y IS NOT NULL AND cites_per_doc_2y IS NOT NULL;


