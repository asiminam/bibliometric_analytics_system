-- Δημιουργία και επιλογή της βάσης δεδομένων
CREATE DATABASE IF NOT EXISTS bookdata_4991
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE bookdata_4991;

-- =========================================================================
-- 1. LOOKUP TABLES (Πίνακες Αναφοράς)
-- =========================================================================

-- Πίνακας Συγγραφέων (Χωρίς συνωνυμίες όπως ζητάει η εκφώνηση)
CREATE TABLE Authors (
    author_id INT AUTO_INCREMENT PRIMARY KEY,
    author_name VARCHAR(255) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- Πίνακας Συνεδρίων (Δεδομένα από iCore26)
CREATE TABLE Conferences (
    conf_id INT AUTO_INCREMENT PRIMARY KEY,
    acronym VARCHAR(50),
    title VARCHAR(255) NOT NULL,
    rank_category VARCHAR(10),       
    primary_for INT                  
) ENGINE=InnoDB;

-- Πίνακας Περιοδικών (Δεδομένα από Kaggle)
CREATE TABLE Journals (
    journal_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    country VARCHAR(100),
    sjr_index DECIMAL(10,3),
    best_quartile VARCHAR(5),        
    total_docs_3y INT,
    total_refs INT,
    cites_per_doc_2y DECIMAL(10,2)
) ENGINE=InnoDB;


-- =========================================================================
-- 2. FACTUAL TABLES (Πίνακες Γεγονότων/Άρθρων)
-- =========================================================================

-- Άρθρα Συνεδρίων (Από dblp)
CREATE TABLE Conference_Articles (
    article_id INT AUTO_INCREMENT PRIMARY KEY,
    original_dblp_id VARCHAR(100),   
    title VARCHAR(500) NOT NULL,
    year INT NOT NULL,
    pages VARCHAR(50),
    conf_id INT NOT NULL,
    FOREIGN KEY (conf_id) REFERENCES Conferences(conf_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Άρθρα Περιοδικών (Από dblp)
CREATE TABLE Journal_Articles (
    article_id INT AUTO_INCREMENT PRIMARY KEY,
    original_dblp_id VARCHAR(100),
    title VARCHAR(500) NOT NULL,
    year INT NOT NULL,
    volume VARCHAR(50),
    pages VARCHAR(50),
    journal_id INT NOT NULL,
    FOREIGN KEY (journal_id) REFERENCES Journals(journal_id) ON DELETE CASCADE
) ENGINE=InnoDB;


-- =========================================================================
-- 3. ΣΧΕΣΕΙΣ ΠΟΛΛΑ-ΠΡΟΣ-ΠΟΛΛΑ (N:M Junction Tables)
-- =========================================================================

-- Σύνδεση Συγγραφέων με Άρθρα Συνεδρίων
CREATE TABLE Conference_Article_Authors (
    article_id INT NOT NULL,
    author_id INT NOT NULL,
    PRIMARY KEY (article_id, author_id),
    FOREIGN KEY (article_id) REFERENCES Conference_Articles(article_id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES Authors(author_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Σύνδεση Συγγραφέων με Άρθρα Περιοδικών
CREATE TABLE Journal_Article_Authors (
    article_id INT NOT NULL,
    author_id INT NOT NULL,
    PRIMARY KEY (article_id, author_id),
    FOREIGN KEY (article_id) REFERENCES Journal_Articles(article_id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES Authors(author_id) ON DELETE CASCADE
) ENGINE=InnoDB;


-- 1. Disable foreign key check
SET FOREIGN_KEY_CHECKS = 0;

-- 2. Load Author Lookup Table
LOAD DATA INFILE 'C:/ProgramData/MySQL/MySQL Server 8.0/Uploads/Authors_Clean.csv'
INTO TABLE Authors
FIELDS TERMINATED BY '\t'       
LINES TERMINATED BY '\r\n'      
(author_id, author_name);

-- 3. Load Relational Table for Conference_Article_Authors
LOAD DATA INFILE 'C:/ProgramData/MySQL/MySQL Server 8.0/Uploads/Conference_Article_Authors_Clean.csv'
INTO TABLE Conference_Article_Authors
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\r\n'
(article_id, author_id);

-- 4. Load Relational Table for Journal_Article_Authors
LOAD DATA INFILE 'C:/ProgramData/MySQL/MySQL Server 8.0/Uploads/Journal_Article_Authors_Clean.csv'
INTO TABLE Journal_Article_Authors
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\r\n'
(article_id, author_id);

-- Reset foreign key check
SET FOREIGN_KEY_CHECKS = 1;