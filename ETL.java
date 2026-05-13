import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ETL for the bibliometric analytics project.
 *
 * What this version fixes compared with the first draft:
 * 1. It creates internal article_id values and uses the SAME ids in the
 *    article tables and in the N:M article-author tables.
 * 2. It also creates clean load files for the lookup tables and the factual
 *    article tables, not only for Authors and relations.
 * 3. It isolates invalid rows in Rejected_Rows.csv instead of silently loading
 *    broken records.
 * 4. It keeps the ETL repeatable: all ids are generated deterministically from
 *    the input processing order.
 * 5. It optionally enriches Conference and Journal lookup tables from ranking
 *    CSV files when such files are present next to the program.
 *
 * Expected input files in the current working directory:
 * - input_inproceedings.csv
 * - input_article.csv
 *
 * Optional input files, if available as CSV:
 * - iCore26_KilledColumnsForLoading.csv
 * - journal_ranking_data_raw.csv
 * - journal_ranking_data_raw/journal_ranking_data_raw.csv
 *
 * Produced tab-delimited load files:
 * - Authors_Clean.csv
 * - Conferences_Clean.csv
 * - Journals_Clean.csv
 * - Conference_Articles_Clean.csv
 * - Journal_Articles_Clean.csv
 * - Conference_Article_Authors_Clean.csv
 * - Journal_Article_Authors_Clean.csv
 * - Rejected_Rows.csv
 */
public class ETL {

    /*
     * Default input names used when the program is started without command-line
     * arguments. A caller can still override them by passing:
     *   java ETL <conference-input.csv> <journal-input.csv>
     */
    private static final String DEFAULT_CONFERENCE_INPUT = "input_inproceedings.csv";
    private static final String DEFAULT_JOURNAL_INPUT = "input_article.csv";

    /*
     * Output file names. The files are written as tab-delimited text because
     * tabs are less likely to appear inside publication titles than commas or
     * semicolons. Null database values are written later as \N.
     */
    private static final String AUTHORS_OUT = "Authors_Clean.csv";
    private static final String CONFERENCES_OUT = "Conferences_Clean.csv";
    private static final String JOURNALS_OUT = "Journals_Clean.csv";
    private static final String CONFERENCE_ARTICLES_OUT = "Conference_Articles_Clean.csv";
    private static final String JOURNAL_ARTICLES_OUT = "Journal_Articles_Clean.csv";
    private static final String CONFERENCE_ARTICLE_AUTHORS_OUT = "Conference_Article_Authors_Clean.csv";
    private static final String JOURNAL_ARTICLE_AUTHORS_OUT = "Journal_Article_Authors_Clean.csv";
    private static final String REJECTED_ROWS_OUT = "Rejected_Rows.csv";

    /*
     * Author lookup state.
     *
     * authorIdsByNormalizedName stores the de-duplication key, for example a
     * lower-case and accent-free version of the author name. authorNamesById
     * stores the display value that will be written to Authors_Clean.csv.
     *
     * LinkedHashMap is used intentionally: it preserves insertion order, so
     * generated ids are deterministic as long as the input row order is stable.
     */
    private final Map<String, Integer> authorIdsByNormalizedName = new LinkedHashMap<>();
    private final Map<Integer, String> authorNamesById = new LinkedHashMap<>();

    /*
     * Conference lookup state.
     *
     * A single conference can be discovered from the ranking file, from the
     * article file, or from both. conferenceIdsByAlias lets the ETL match either
     * acronym or title to the same generated conference_id.
     */
    private final Map<String, Integer> conferenceIdsByAlias = new LinkedHashMap<>();
    private final Map<Integer, Conference> conferencesById = new LinkedHashMap<>();

    /*
     * Journal lookup state.
     *
     * Journals are matched primarily by normalized title. The alias map allows
     * the ranking data and article data to enrich the same Journal object.
     */
    private final Map<String, Integer> journalIdsByAlias = new LinkedHashMap<>();
    private final Map<Integer, Journal> journalsById = new LinkedHashMap<>();

    /*
     * Relation de-duplication state.
     *
     * Each set stores keys in the form "<article_id>-<author_id>". This avoids
     * writing duplicate rows when the same article/author relationship appears
     * more than once in the source data.
     */
    private final Set<String> seenConferenceArticleAuthorRelations = new LinkedHashSet<>();
    private final Set<String> seenJournalArticleAuthorRelations = new LinkedHashSet<>();

    // original_dblp_id -> generated article_id. Used to avoid duplicate article rows
    // while still allowing us to merge any additional author relations.
    private final Map<String, Integer> conferenceArticleIdsByOriginalId = new HashMap<>();
    private final Map<String, Integer> journalArticleIdsByOriginalId = new HashMap<>();

    /*
     * Monotonic id counters for generated primary keys. They start at 1 so the
     * output files can be loaded directly into database tables that expect
     * positive integer ids.
     */
    private int nextAuthorId = 1;
    private int nextConferenceId = 1;
    private int nextJournalId = 1;
    private int nextConferenceArticleId = 1;
    private int nextJournalArticleId = 1;

    /*
     * Writers are opened once in run() and kept as fields so helper methods can
     * stream rows immediately. This keeps memory usage low for the large article
     * tables while lookup tables are still accumulated in maps.
     */
    private BufferedWriter conferenceArticlesWriter;
    private BufferedWriter journalArticlesWriter;
    private BufferedWriter conferenceArticleAuthorsWriter;
    private BufferedWriter journalArticleAuthorsWriter;
    private BufferedWriter rejectedRowsWriter;

    /**
     * Program entry point.
     *
     * The first optional argument is the conference article input file and the
     * second optional argument is the journal article input file. If arguments
     * are not provided, the default project file names are used.
     */
    public static void main(String[] args) {
        String conferenceInput = args.length >= 1 ? args[0] : DEFAULT_CONFERENCE_INPUT;
        String journalInput = args.length >= 2 ? args[1] : DEFAULT_JOURNAL_INPUT;

        ETL etl = new ETL();
        try {
            etl.run(conferenceInput, journalInput);
            System.out.println("\nETL completed successfully.");
            System.out.println("Generated load files:");
            System.out.println("- " + AUTHORS_OUT);
            System.out.println("- " + CONFERENCES_OUT);
            System.out.println("- " + JOURNALS_OUT);
            System.out.println("- " + CONFERENCE_ARTICLES_OUT);
            System.out.println("- " + JOURNAL_ARTICLES_OUT);
            System.out.println("- " + CONFERENCE_ARTICLE_AUTHORS_OUT);
            System.out.println("- " + JOURNAL_ARTICLE_AUTHORS_OUT);
            System.out.println("- " + REJECTED_ROWS_OUT);
        } catch (IOException e) {
            System.err.println("Fatal ETL error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the complete ETL pipeline.
     *
     * The pipeline is intentionally ordered:
     * 1. Load optional conference and journal ranking data first, so later
     *    article rows can reuse and enrich the same lookup records.
     * 2. Open the large output writers and stream article/relation rows while
     *    processing the source files.
     * 3. Close streamed writers, then write lookup tables from the maps that
     *    were built during processing.
     *
     * @param conferenceInput path to the DBLP inproceedings-style CSV file
     * @param journalInput path to the DBLP article-style CSV file
     * @throws IOException if an input file cannot be read or an output file
     *         cannot be written
     */
    private void run(String conferenceInput, String journalInput) throws IOException {
        // Load enrichment files before article processing so ranking metadata
        // can be attached to the same conference/journal ids used by articles.
        loadOptionalConferenceRankings();
        loadOptionalJournalRankings();

        // Article and relation outputs can become large, so they are streamed
        // directly to disk instead of being stored in memory.
        try (
            BufferedWriter confArticles = newUtf8Writer(CONFERENCE_ARTICLES_OUT);
            BufferedWriter journalArticles = newUtf8Writer(JOURNAL_ARTICLES_OUT);
            BufferedWriter confArticleAuthors = newUtf8Writer(CONFERENCE_ARTICLE_AUTHORS_OUT);
            BufferedWriter journalArticleAuthors = newUtf8Writer(JOURNAL_ARTICLE_AUTHORS_OUT);
            BufferedWriter rejected = newUtf8Writer(REJECTED_ROWS_OUT)
        ) {
            this.conferenceArticlesWriter = confArticles;
            this.journalArticlesWriter = journalArticles;
            this.conferenceArticleAuthorsWriter = confArticleAuthors;
            this.journalArticleAuthorsWriter = journalArticleAuthors;
            this.rejectedRowsWriter = rejected;

            // Rejected_Rows.csv always starts with a header so data-quality
            // problems can be inspected independently after the run.
            writeRejectedHeader();

            System.out.println("Starting ETL for conferences: " + conferenceInput);
            processConferenceArticles(conferenceInput);

            System.out.println("\nStarting ETL for journals: " + journalInput);
            processJournalArticles(journalInput);
        }

        // Lookup tables depend on all source rows because new authors,
        // conferences, and journals may be discovered at any point.
        writeLookupTables();

        System.out.println("\nSummary");
        System.out.println("Unique authors: " + authorNamesById.size());
        System.out.println("Conferences: " + conferencesById.size());
        System.out.println("Journals: " + journalsById.size());
        System.out.println("Conference articles: " + conferenceArticleIdsByOriginalId.size());
        System.out.println("Journal articles: " + journalArticleIdsByOriginalId.size());
        System.out.println("Conference article-author relations: " + seenConferenceArticleAuthorRelations.size());
        System.out.println("Journal article-author relations: " + seenJournalArticleAuthorRelations.size());
    }

    /**
     * Reads the conference/inproceedings input file and writes cleaned
     * conference article rows plus article-author relation rows.
     *
     * Each accepted source row must have an original DBLP id, title, valid year,
     * and conference/booktitle value. Rows that fail these checks are written to
     * Rejected_Rows.csv with the original raw line.
     */
    private void processConferenceArticles(String inputFile) throws IOException {
        Path inputPath = Paths.get(inputFile);
        if (!Files.exists(inputPath)) {
            System.err.println("File not found, skipping: " + inputFile);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            // The source data may be comma-, semicolon-, or tab-delimited. The
            // delimiter is inferred from the header, then reused for every row.
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // Resolve required and optional columns by normalized header names.
            // Multiple candidate names are accepted to support slightly
            // different exports without editing the code.
            int idIndex = requiredIndex(headerIndex, inputFile, "id", "original_dblp_id", "dblp_id");
            int authorsIndex = optionalIndex(headerIndex, "authors", "author");
            int titleIndex = requiredIndex(headerIndex, inputFile, "title", "paper_title");
            int yearIndex = requiredIndex(headerIndex, inputFile, "year");
            int pagesIndex = optionalIndex(headerIndex, "pages");
            int booktitleIndex = requiredIndex(headerIndex, inputFile, "booktitle", "conference", "acronym", "venue");

            String line;
            int lineNumber = 1;
            int processed = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                List<String> columns = splitDelimitedLine(line, delimiter);

                // Extract and lightly clean all fields needed by the target
                // schema. valueAt() returns null for missing optional columns.
                String originalDblpId = valueAt(columns, idIndex);
                String title = valueAt(columns, titleIndex);
                String yearText = valueAt(columns, yearIndex);
                String pages = valueAt(columns, pagesIndex);
                String booktitle = valueAt(columns, booktitleIndex);
                String authors = valueAt(columns, authorsIndex);

                // Reject rows that would violate required database fields or
                // create article records that cannot be joined back to DBLP.
                if (isNullLike(originalDblpId)) {
                    reject(inputFile, lineNumber, "Missing original DBLP id", line);
                    continue;
                }
                if (isNullLike(title)) {
                    reject(inputFile, lineNumber, "Missing conference article title", line);
                    continue;
                }
                Integer year = parseYear(yearText);
                if (year == null) {
                    reject(inputFile, lineNumber, "Invalid or missing year: " + safe(yearText), line);
                    continue;
                }
                if (isNullLike(booktitle)) {
                    reject(inputFile, lineNumber, "Missing conference/booktitle value", line);
                    continue;
                }

                // Create/reuse lookup ids, then stream the fact row and its
                // N:M author relationships to the output files.
                int conferenceId = getOrCreateConference(booktitle, booktitle, null, null);
                int articleId = getOrCreateConferenceArticle(originalDblpId, title, year, pages, conferenceId);
                writeAuthorRelations(authors, articleId, conferenceArticleAuthorsWriter, seenConferenceArticleAuthorRelations);
                processed++;
            }

            System.out.println("Processed conference rows: " + processed);
        }
    }

    /**
     * Reads the journal-article input file and writes cleaned journal article
     * rows plus article-author relation rows.
     *
     * This method mirrors processConferenceArticles(), but it expects a journal
     * title instead of a conference/booktitle value and also supports the
     * optional volume column used by journal publications.
     */
    private void processJournalArticles(String inputFile) throws IOException {
        Path inputPath = Paths.get(inputFile);
        if (!Files.exists(inputPath)) {
            System.err.println("File not found, skipping: " + inputFile);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            // Detect the delimiter once from the header. The custom parser below
            // understands quoted values, so delimiters inside titles are safe.
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // Column matching is intentionally tolerant of common name variants
            // such as "dblp_id" versus "original_dblp_id".
            int idIndex = requiredIndex(headerIndex, inputFile, "id", "original_dblp_id", "dblp_id");
            int authorsIndex = optionalIndex(headerIndex, "authors", "author");
            int titleIndex = requiredIndex(headerIndex, inputFile, "title", "paper_title");
            int yearIndex = requiredIndex(headerIndex, inputFile, "year");
            int pagesIndex = optionalIndex(headerIndex, "pages");
            int volumeIndex = optionalIndex(headerIndex, "volume", "vol");
            int journalIndex = requiredIndex(headerIndex, inputFile, "journal", "journal_title", "venue");

            String line;
            int lineNumber = 1;
            int processed = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                List<String> columns = splitDelimitedLine(line, delimiter);

                // Pull the raw row into named variables before validation. This
                // keeps the checks below aligned with the database fields.
                String originalDblpId = valueAt(columns, idIndex);
                String title = valueAt(columns, titleIndex);
                String yearText = valueAt(columns, yearIndex);
                String pages = valueAt(columns, pagesIndex);
                String volume = valueAt(columns, volumeIndex);
                String journalTitle = valueAt(columns, journalIndex);
                String authors = valueAt(columns, authorsIndex);

                // Required fields are validated before any output is written,
                // so rejected rows never produce partial article/relation data.
                if (isNullLike(originalDblpId)) {
                    reject(inputFile, lineNumber, "Missing original DBLP id", line);
                    continue;
                }
                if (isNullLike(title)) {
                    reject(inputFile, lineNumber, "Missing journal article title", line);
                    continue;
                }
                Integer year = parseYear(yearText);
                if (year == null) {
                    reject(inputFile, lineNumber, "Invalid or missing year: " + safe(yearText), line);
                    continue;
                }
                if (isNullLike(journalTitle)) {
                    reject(inputFile, lineNumber, "Missing journal value", line);
                    continue;
                }

                // The journal lookup may already exist from the ranking file;
                // otherwise a new lookup row is created from the article venue.
                int journalId = getOrCreateJournal(journalTitle, null, null, null, null, null, null);
                int articleId = getOrCreateJournalArticle(originalDblpId, title, year, volume, pages, journalId);
                writeAuthorRelations(authors, articleId, journalArticleAuthorsWriter, seenJournalArticleAuthorRelations);
                processed++;
            }

            System.out.println("Processed journal rows: " + processed);
        }
    }

    /**
     * Returns the generated id for a conference, creating a new conference row
     * when no matching acronym/title alias exists yet.
     *
     * Ranking data and article data can arrive in either order. If the
     * conference already exists, this method fills missing ranking fields
     * without overwriting values that were already loaded.
     */
    private int getOrCreateConference(String acronym, String title, String rankCategory, String primaryFor) {
        String cleanedAcronym = cleanText(acronym);
        String cleanedTitle = cleanText(title);
        String lookupValue = !isNullLike(cleanedAcronym) ? cleanedAcronym : cleanedTitle;
        String key = normalizeLookupKey(lookupValue);

        Integer existingId = conferenceIdsByAlias.get(key);
        if (existingId != null) {
            Conference existing = conferencesById.get(existingId);
            // Preserve existing metadata, but enrich blank fields when a later
            // ranking row provides information that article rows did not have.
            if (isNullLike(existing.rankCategory) && !isNullLike(rankCategory)) {
                existing.rankCategory = cleanText(rankCategory);
            }
            if (isNullLike(existing.primaryFor) && !isNullLike(primaryFor)) {
                existing.primaryFor = cleanText(primaryFor);
            }
            addConferenceAliases(existingId, cleanedAcronym, cleanedTitle);
            return existingId;
        }

        // No alias matched, so create a new generated conference_id and remember
        // both acronym and title as future lookup keys.
        int id = nextConferenceId++;
        Conference conference = new Conference(id, cleanedAcronym, cleanedTitle, cleanText(rankCategory), cleanText(primaryFor));
        conferencesById.put(id, conference);
        addConferenceAliases(id, cleanedAcronym, cleanedTitle);
        return id;
    }

    /**
     * Returns the generated id for a journal, creating a new journal row when no
     * matching or similar title is known.
     *
     * Journal ranking files often use slightly different title strings than
     * DBLP. The exact alias map is checked first, then findSimilarJournalId()
     * performs a conservative containment match for long titles.
     */
    private int getOrCreateJournal(String title, String country, String sjrIndex, String bestQuartile,
                                   String totalDocs3y, String totalRefs, String citesPerDoc2y) {
        String cleanedTitle = cleanText(title);
        String key = normalizeLookupKey(cleanedTitle);

        Integer existingId = journalIdsByAlias.get(key);
        if (existingId == null) {
            existingId = findSimilarJournalId(cleanedTitle);
        }

        if (existingId != null) {
            // Reuse the existing journal_id and add any ranking attributes that
            // are still blank on the lookup record.
            Journal existing = journalsById.get(existingId);
            updateIfMissing(existing, country, sjrIndex, bestQuartile, totalDocs3y, totalRefs, citesPerDoc2y);
            addJournalAliases(existingId, cleanedTitle);
            return existingId;
        }

        // The title is new to this run. Clean numeric ranking fields before
        // storing them so the output is ready for database loading.
        int id = nextJournalId++;
        Journal journal = new Journal(
            id,
            cleanedTitle,
            cleanText(country),
            cleanDecimal(sjrIndex),
            cleanText(bestQuartile),
            cleanInteger(totalDocs3y),
            cleanInteger(totalRefs),
            cleanDecimal(citesPerDoc2y)
        );
        journalsById.put(id, journal);
        addJournalAliases(id, cleanedTitle);
        return id;
    }

    /**
     * Adds journal ranking/enrichment values only when the current Journal
     * object does not already have a value. This prevents later sparse rows from
     * replacing richer metadata loaded earlier in the run.
     */
    private void updateIfMissing(Journal existing, String country, String sjrIndex, String bestQuartile,
                                 String totalDocs3y, String totalRefs, String citesPerDoc2y) {
        if (isNullLike(existing.country) && !isNullLike(country)) {
            existing.country = cleanText(country);
        }
        if (isNullLike(existing.sjrIndex) && !isNullLike(sjrIndex)) {
            existing.sjrIndex = cleanDecimal(sjrIndex);
        }
        if (isNullLike(existing.bestQuartile) && !isNullLike(bestQuartile)) {
            existing.bestQuartile = cleanText(bestQuartile);
        }
        if (isNullLike(existing.totalDocs3y) && !isNullLike(totalDocs3y)) {
            existing.totalDocs3y = cleanInteger(totalDocs3y);
        }
        if (isNullLike(existing.totalRefs) && !isNullLike(totalRefs)) {
            existing.totalRefs = cleanInteger(totalRefs);
        }
        if (isNullLike(existing.citesPerDoc2y) && !isNullLike(citesPerDoc2y)) {
            existing.citesPerDoc2y = cleanDecimal(citesPerDoc2y);
        }
    }

    /**
     * Returns the generated id for a conference article and writes the article
     * row the first time the original DBLP id is seen.
     *
     * Duplicate source rows reuse the same article_id, which allows any missing
     * author relationships from later duplicate rows to be merged without
     * creating duplicate article records.
     */
    private int getOrCreateConferenceArticle(String originalDblpId, String title, int year, String pages, int conferenceId)
            throws IOException {
        String cleanedOriginalId = cleanText(originalDblpId);
        Integer existingId = conferenceArticleIdsByOriginalId.get(cleanedOriginalId);
        if (existingId != null) {
            return existingId;
        }

        int articleId = nextConferenceArticleId++;
        conferenceArticleIdsByOriginalId.put(cleanedOriginalId, articleId);

        // The target order matches the expected Conference_Articles table load
        // format: article id, original DBLP id, title, year, pages, conference.
        writeTsvLine(conferenceArticlesWriter,
            String.valueOf(articleId),
            cleanedOriginalId,
            cleanText(title),
            String.valueOf(year),
            cleanText(pages),
            String.valueOf(conferenceId)
        );

        return articleId;
    }

    /**
     * Returns the generated id for a journal article and writes the article row
     * the first time the original DBLP id is seen.
     *
     * This mirrors getOrCreateConferenceArticle(), with volume included because
     * journal articles commonly store publication volume separately.
     */
    private int getOrCreateJournalArticle(String originalDblpId, String title, int year, String volume, String pages, int journalId)
            throws IOException {
        String cleanedOriginalId = cleanText(originalDblpId);
        Integer existingId = journalArticleIdsByOriginalId.get(cleanedOriginalId);
        if (existingId != null) {
            return existingId;
        }

        int articleId = nextJournalArticleId++;
        journalArticleIdsByOriginalId.put(cleanedOriginalId, articleId);

        // The target order matches the expected Journal_Articles table load
        // format: article id, original DBLP id, title, year, volume, pages, journal.
        writeTsvLine(journalArticlesWriter,
            String.valueOf(articleId),
            cleanedOriginalId,
            cleanText(title),
            String.valueOf(year),
            cleanText(volume),
            cleanText(pages),
            String.valueOf(journalId)
        );

        return articleId;
    }

    /**
     * Splits the pipe-separated authors field, creates/reuses author ids, and
     * writes article-author relation rows.
     *
     * The method is shared by conference and journal processing. The caller
     * supplies the correct output writer and "seen" set for the article type.
     */
    private void writeAuthorRelations(String authorsValue, int articleId, BufferedWriter relationWriter, Set<String> seenRelations)
            throws IOException {
        if (isNullLike(authorsValue)) {
            return;
        }

        // The input files store multiple authors in one column separated by |.
        String[] authors = authorsValue.split("\\|");
        for (String authorName : authors) {
            String cleanedAuthorName = cleanText(authorName);
            if (isNullLike(cleanedAuthorName)) {
                continue;
            }

            int authorId = getOrCreateAuthor(cleanedAuthorName);
            String relationKey = articleId + "-" + authorId;
            // LinkedHashSet.add() returns false when the relation has already
            // been written, which protects the relation table from duplicates.
            if (seenRelations.add(relationKey)) {
                writeTsvLine(relationWriter, String.valueOf(articleId), String.valueOf(authorId));
            }
        }
    }

    /**
     * Returns the generated id for an author, creating a new author row when the
     * normalized name has not appeared before.
     *
     * The normalized key is used only for matching. The output keeps the cleaned
     * original spelling from the first occurrence.
     */
    private int getOrCreateAuthor(String authorName) {
        String normalizedAuthorName = normalizeAuthorName(authorName);
        Integer existingId = authorIdsByNormalizedName.get(normalizedAuthorName);
        if (existingId != null) {
            return existingId;
        }

        int id = nextAuthorId++;
        authorIdsByNormalizedName.put(normalizedAuthorName, id);
        authorNamesById.put(id, authorName);
        return id;
    }

    /**
     * Searches for an optional conference-ranking CSV and loads the first file
     * that exists.
     *
     * Only standard Java libraries are used in this ETL, so Excel files are not
     * parsed directly. If the Excel version is found, the user is told to export
     * it as CSV.
     */
    private void loadOptionalConferenceRankings() {
        List<String> possibleFiles = Arrays.asList(
            "iCore26_KilledColumnsForLoading.csv",
            "icore26_KilledColumnsForLoading.csv",
            "iCore26.csv",
            "icore26.csv",
            "iCORE_raw.csv"
        );

        for (String file : possibleFiles) {
            Path path = Paths.get(file);
            if (!Files.exists(path)) {
                continue;
            }

            try {
                // Stop after the first usable ranking file to avoid loading the
                // same ranking source twice under different filenames.
                int loaded = loadConferenceRankingCsv(path);
                System.out.println("Loaded conference ranking rows from " + file + ": " + loaded);
                return;
            } catch (IOException e) {
                System.err.println("Could not load conference ranking file " + file + ": " + e.getMessage());
            }
        }

        if (Files.exists(Paths.get("icoreCategories.xlsx"))) {
            System.out.println("Found icoreCategories.xlsx, but this ETL uses only standard Java and reads CSV files. "
                + "Export the Excel sheet as CSV if you want the conference ranking fields enriched automatically.");
        }
    }

    /**
     * Loads conference lookup rows from a ranking CSV file.
     *
     * The ranking file is treated as enrichment data. It can create conference
     * rows before article processing, and article rows can later reuse those ids
     * through acronym/title aliases.
     *
     * @return number of non-empty ranking rows consumed
     */
    private int loadConferenceRankingCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return 0;
            }

            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // These columns are optional because ranking CSV exports may use
            // different subsets or slightly different names.
            int titleIndex = optionalIndex(headerIndex, "title", "conference_title", "name");
            int acronymIndex = optionalIndex(headerIndex, "acronym", "abbr", "abbreviation");
            int rankIndex = optionalIndex(headerIndex, "rank", "rank_category", "ranking");
            int primaryForIndex = optionalIndex(headerIndex, "primaryfor", "primary_for", "primary_for_code");

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                List<String> columns = splitDelimitedLine(line, delimiter);
                String title = valueAt(columns, titleIndex);
                String acronym = valueAt(columns, acronymIndex);
                String rank = valueAt(columns, rankIndex);
                String primaryFor = valueAt(columns, primaryForIndex);

                if (isNullLike(title) && isNullLike(acronym)) {
                    continue;
                }

                // Use the acronym as the primary lookup when present, but keep
                // the title too so article rows can match either value.
                getOrCreateConference(acronym, isNullLike(title) ? acronym : title, rank, primaryFor);
                count++;
            }
            return count;
        }
    }

    /**
     * Searches for an optional journal-ranking CSV and loads the first file that
     * exists. The two supported paths cover both a flat export and a nested
     * folder layout.
     */
    private void loadOptionalJournalRankings() {
        List<String> possibleFiles = Arrays.asList(
            "journal_ranking_data_raw.csv",
            "journal_ranking_data_raw/journal_ranking_data_raw.csv"
        );

        for (String file : possibleFiles) {
            Path path = Paths.get(file);
            if (!Files.exists(path)) {
                continue;
            }

            try {
                // As with conferences, one ranking file is enough. Loading both
                // could double-count equivalent journal metadata.
                int loaded = loadJournalRankingCsv(path);
                System.out.println("Loaded journal ranking rows from " + file + ": " + loaded);
                return;
            } catch (IOException e) {
                System.err.println("Could not load journal ranking file " + file + ": " + e.getMessage());
            }
        }
    }

    /**
     * Loads journal lookup rows from a ranking CSV file.
     *
     * Numeric ranking columns are stored as strings after validation/cleaning
     * because the ETL output is a load file. Database type enforcement happens
     * later when the TSV files are imported.
     *
     * @return number of ranking rows with a usable journal title
     */
    private int loadJournalRankingCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return 0;
            }

            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // Support common column-name variants used by SJR/SCImago-style
            // exports and project-specific cleaned files.
            int titleIndex = optionalIndex(headerIndex, "title", "journal_title", "source_title", "journal");
            int countryIndex = optionalIndex(headerIndex, "country");
            int sjrIndex = optionalIndex(headerIndex, "sjrindex", "sjr_index", "sjr");
            int bestQuartileIndex = optionalIndex(headerIndex, "bestquartile", "best_quartile", "quartile");
            int totalDocs3yIndex = optionalIndex(headerIndex, "totaldocs3y", "total_docs_3y", "citabledocs3y", "citable_docs_3y");
            int totalRefsIndex = optionalIndex(headerIndex, "totalrefs", "total_refs");
            int citesPerDoc2yIndex = optionalIndex(headerIndex, "citesdoc2y", "cites_per_doc_2y", "citesperdoc2y");

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                List<String> columns = splitDelimitedLine(line, delimiter);
                String title = valueAt(columns, titleIndex);
                if (isNullLike(title)) {
                    continue;
                }

                getOrCreateJournal(
                    title,
                    valueAt(columns, countryIndex),
                    valueAt(columns, sjrIndex),
                    valueAt(columns, bestQuartileIndex),
                    valueAt(columns, totalDocs3yIndex),
                    valueAt(columns, totalRefsIndex),
                    valueAt(columns, citesPerDoc2yIndex)
                );
                count++;
            }
            return count;
        }
    }

    /**
     * Writes lookup tables after all input rows have been processed.
     *
     * Article and relation rows are streamed during processing, but lookup rows
     * must wait until the end so every discovered author, conference, and
     * journal is included exactly once.
     */
    private void writeLookupTables() throws IOException {
        try (BufferedWriter writer = newUtf8Writer(AUTHORS_OUT)) {
            for (Map.Entry<Integer, String> entry : authorNamesById.entrySet()) {
                writeTsvLine(writer, String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        try (BufferedWriter writer = newUtf8Writer(CONFERENCES_OUT)) {
            for (Conference conference : conferencesById.values()) {
                writeTsvLine(writer,
                    String.valueOf(conference.id),
                    conference.acronym,
                    conference.title,
                    conference.rankCategory,
                    cleanInteger(conference.primaryFor)
                );
            }
        }

        try (BufferedWriter writer = newUtf8Writer(JOURNALS_OUT)) {
            for (Journal journal : journalsById.values()) {
                writeTsvLine(writer,
                    String.valueOf(journal.id),
                    journal.title,
                    journal.country,
                    journal.sjrIndex,
                    journal.bestQuartile,
                    journal.totalDocs3y,
                    journal.totalRefs,
                    journal.citesPerDoc2y
                );
            }
        }
    }

    /**
     * Registers all known lookup names for a conference id. This lets a later
     * article row match the same conference by acronym or by full title.
     */
    private void addConferenceAliases(int conferenceId, String acronym, String title) {
        addAlias(conferenceIdsByAlias, acronym, conferenceId);
        addAlias(conferenceIdsByAlias, title, conferenceId);
    }

    /**
     * Registers a journal title as an alias for a generated journal id.
     */
    private void addJournalAliases(int journalId, String title) {
        addAlias(journalIdsByAlias, title, journalId);
    }

    /**
     * Adds a normalized alias to a lookup map if the alias is usable.
     *
     * putIfAbsent() protects the first id assigned to an alias. That keeps
     * deterministic ids stable and avoids accidentally moving an alias from one
     * lookup row to another later in the run.
     */
    private void addAlias(Map<String, Integer> aliases, String value, int id) {
        if (isNullLike(value)) {
            return;
        }
        aliases.putIfAbsent(normalizeLookupKey(value), id);
    }

    /**
     * Performs a conservative fuzzy match for journal titles.
     *
     * Very short titles are ignored because containment matching would be too
     * risky. Longer titles can match when either normalized title contains the
     * other, which helps connect ranking names and DBLP names that differ by a
     * subtitle or minor suffix.
     */
    private Integer findSimilarJournalId(String journalTitle) {
        String requested = normalizeLookupKey(journalTitle);
        if (requested.length() < 8) {
            return null;
        }

        for (Map.Entry<String, Integer> entry : journalIdsByAlias.entrySet()) {
            String existing = entry.getKey();
            if (existing.length() < 8) {
                continue;
            }
            if (existing.contains(requested) || requested.contains(existing)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Writes the header row for the rejected-row audit file.
     */
    private void writeRejectedHeader() throws IOException {
        writeTsvLine(rejectedRowsWriter, "source_file", "line_number", "reason", "raw_row");
    }

    /**
     * Records a source row that could not be safely loaded.
     *
     * The raw line is preserved so the data issue can be inspected or corrected
     * without needing to cross-reference the original input file manually.
     */
    private void reject(String sourceFile, int lineNumber, String reason, String rawLine) throws IOException {
        writeTsvLine(rejectedRowsWriter, sourceFile, String.valueOf(lineNumber), reason, rawLine);
    }

    /**
     * Opens a UTF-8 writer for an output file in the current working directory.
     */
    private static BufferedWriter newUtf8Writer(String outputFile) throws IOException {
        return Files.newBufferedWriter(Paths.get(outputFile), StandardCharsets.UTF_8);
    }

    /**
     * Writes one tab-separated output row.
     *
     * Every field goes through toTsvField() so null-like values, embedded tabs,
     * line breaks, and repeated whitespace are handled consistently across all
     * generated load files.
     */
    private static void writeTsvLine(BufferedWriter writer, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write('\t');
            }
            writer.write(toTsvField(values[i]));
        }
        writer.newLine();
    }

    /**
     * Converts a Java value into a safe TSV field.
     *
     * Database nulls are represented as \N. Other values are kept unquoted but
     * normalized to one physical line so bulk loading does not split records.
     */
    private static String toTsvField(String value) {
        if (isNullLike(value)) {
            return "\\N";
        }
        return value
            .replace('\t', ' ')
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Builds a map from normalized header name to column position.
     *
     * Normalization removes case and punctuation differences so headers such as
     * "Original DBLP ID", "original_dblp_id", and "original-dblp-id" can be
     * matched by the same candidate string.
     */
    private static Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(normalizeHeader(headers.get(i)), i);
        }
        return index;
    }

    /**
     * Finds a required column index or throws an explanatory error.
     */
    private static int requiredIndex(Map<String, Integer> headerIndex, String fileName, String... candidates) {
        int index = optionalIndex(headerIndex, candidates);
        if (index < 0) {
            throw new IllegalArgumentException(
                "Missing required column in " + fileName + ": one of " + Arrays.toString(candidates)
            );
        }
        return index;
    }

    /**
     * Finds the first available candidate column name.
     *
     * @return the zero-based column index, or -1 when none of the candidates is
     *         present
     */
    private static int optionalIndex(Map<String, Integer> headerIndex, String... candidates) {
        for (String candidate : candidates) {
            Integer index = headerIndex.get(normalizeHeader(candidate));
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Safely reads a column value from a parsed row.
     *
     * Missing optional columns and short rows return null, which later becomes a
     * database null in output or is handled by validation.
     */
    private static String valueAt(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        return cleanText(values.get(index));
    }

    /**
     * Guesses the delimiter used by a CSV/TSV header.
     *
     * The delimiter with the highest count outside quoted text wins. Semicolon
     * is the default tie-breaker because many European spreadsheet exports use
     * semicolon-separated CSV.
     */
    private static char detectDelimiter(String headerLine) {
        char[] candidates = new char[] {';', ',', '\t'};
        char best = ';';
        int bestCount = -1;

        for (char candidate : candidates) {
            int count = countDelimiterOutsideQuotes(headerLine, candidate);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Counts delimiter occurrences while ignoring delimiters inside quoted
     * fields. Escaped double quotes inside quoted fields are skipped.
     */
    private static int countDelimiterOutsideQuotes(String line, char delimiter) {
        boolean inQuotes = false;
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    /**
     * Splits one delimited line using a small CSV parser.
     *
     * The parser supports quoted fields and doubled quotes, which is enough for
     * the project CSV files without adding an external dependency.
     */
    private static List<String> splitDelimitedLine(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    /**
     * Normalizes a header so candidate names can be compared robustly.
     *
     * The byte order mark is removed because UTF-8 CSV files exported from
     * spreadsheets sometimes include it at the beginning of the first header.
     */
    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\uFEFF", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Cleans ordinary text fields by removing a possible byte order mark,
     * trimming outer whitespace, and collapsing repeated whitespace.
     *
     * Null-like values are returned as null so all later checks can use the same
     * isNullLike() logic.
     */
    private static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
            .replace("\uFEFF", "")
            .trim()
            .replaceAll("\\s+", " ");
        return isNullLike(cleaned) ? null : cleaned;
    }

    /**
     * Defines the set of source values that should be treated as missing data.
     */
    private static boolean isNullLike(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || trimmed.equalsIgnoreCase("NULL")
            || trimmed.equalsIgnoreCase("N/A")
            || trimmed.equalsIgnoreCase("NA")
            || trimmed.equals("\\N")
            || trimmed.equals("-");
    }

    /**
     * Returns a printable string for messages where null would be confusing.
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Parses a publication year from a source value.
     *
     * Exact four-digit years are accepted directly. Values that contain a
     * four-digit year inside extra text are also accepted, which handles simple
     * dirty exports without rejecting otherwise usable rows.
     */
    private static Integer parseYear(String value) {
        if (isNullLike(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}")) {
            return Integer.valueOf(trimmed);
        }
        String digits = trimmed.replaceAll(".*?(\\d{4}).*", "$1");
        if (digits.matches("\\d{4}")) {
            return Integer.valueOf(digits);
        }
        return null;
    }

    /**
     * Cleans an integer field for database loading.
     *
     * Thousands separators are removed. Decimal values that are mathematically
     * integers, such as "42.0", are converted to "42".
     */
    private static String cleanInteger(String value) {
        if (isNullLike(value)) {
            return null;
        }
        String trimmed = value.trim().replace(",", "");
        if (trimmed.matches("-?\\d+")) {
            return trimmed;
        }
        if (trimmed.matches("-?\\d+\\.0+")) {
            return trimmed.substring(0, trimmed.indexOf('.'));
        }
        return null;
    }

    /**
     * Cleans a decimal field for database loading.
     *
     * Commas are converted to decimal points so European-style decimal values
     * can be loaded into numeric database columns.
     */
    private static String cleanDecimal(String value) {
        if (isNullLike(value)) {
            return null;
        }
        String trimmed = value.trim().replace(",", ".");
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        return null;
    }

    /**
     * Produces a normalized key for conference and journal matching.
     *
     * This starts with author-style normalization for case/accent handling, then
     * removes punctuation and common venue words that usually do not distinguish
     * one conference or journal from another.
     */
    private static String normalizeLookupKey(String value) {
        if (isNullLike(value)) {
            return "";
        }
        String normalized = normalizeAuthorName(value);
        normalized = normalized.replace("&", "and");
        normalized = normalized.replaceAll("[^a-z0-9]+", " ");
        normalized = normalized.replaceAll("\\b(the|of|and|for|on|in|journal|transactions|proceedings|conference|international)\\b", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    /**
     * Normalizes author names for de-duplication.
     *
     * The method lowercases, fixes a few common encoding artifacts, removes
     * diacritics, and collapses whitespace. The original cleaned spelling is
     * still preserved for output.
     */
    private static String normalizeAuthorName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);

        // Replace common non-ASCII characters with ASCII equivalents before
        // removing diacritics, which makes name matching more consistent.
        normalized = normalized.replace("ß", "ss");
        normalized = normalized.replace("æ", "ae");
        normalized = normalized.replace("œ", "oe");
        normalized = normalized.replace("ø", "o");
        normalized = normalized.replace("đ", "d");
        normalized = normalized.replace("ł", "l");

        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized.trim();
    }

    /**
     * In-memory representation of a conference lookup row.
     *
     * Ranking fields are mutable because a conference may be created from an
     * article row first and enriched later by ranking data, or vice versa.
     */
    private static final class Conference {
        private final int id;
        private final String acronym;
        private final String title;
        private String rankCategory;
        private String primaryFor;

        private Conference(int id, String acronym, String title, String rankCategory, String primaryFor) {
            this.id = id;
            this.acronym = acronym;
            this.title = isNullLike(title) ? acronym : title;
            this.rankCategory = rankCategory;
            this.primaryFor = primaryFor;
        }
    }

    /**
     * In-memory representation of a journal lookup row.
     *
     * Ranking/enrichment fields are mutable for the same reason as Conference:
     * the best metadata for a journal can arrive from a different source row
     * than the one that first creates the lookup record.
     */
    private static final class Journal {
        private final int id;
        private final String title;
        private String country;
        private String sjrIndex;
        private String bestQuartile;
        private String totalDocs3y;
        private String totalRefs;
        private String citesPerDoc2y;

        private Journal(int id, String title, String country, String sjrIndex, String bestQuartile,
                        String totalDocs3y, String totalRefs, String citesPerDoc2y) {
            this.id = id;
            this.title = title;
            this.country = country;
            this.sjrIndex = sjrIndex;
            this.bestQuartile = bestQuartile;
            this.totalDocs3y = totalDocs3y;
            this.totalRefs = totalRefs;
            this.citesPerDoc2y = citesPerDoc2y;
        }
    }
}
