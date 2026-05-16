package src;
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

// this class cleans the CSV files of the project

// Here we read the raw files, clean the values, create ids, remove duplicates,
// and write the final files that can be loaded into MySQL
public class ETL {

    // input files used when file names aren't used in args
    private static final String DEFAULT_CONFERENCE_INPUT = "data/raw/input_inproceedings.csv";
    private static final String DEFAULT_JOURNAL_INPUT = "data/raw/input_article.csv";

    // output files that the program creates
    private static final String AUTHORS_OUT = "data/processed/Authors_Clean.csv";
    private static final String CONFERENCES_OUT = "data/processed/Conferences_Clean.csv";
    private static final String JOURNALS_OUT = "data/processed/Journals_Clean.csv";
    private static final String CONFERENCE_ARTICLES_OUT = "data/processed/Conference_Articles_Clean.csv";
    private static final String JOURNAL_ARTICLES_OUT = "data/processed/Journal_Articles_Clean.csv";
    private static final String CONFERENCE_ARTICLE_AUTHORS_OUT = "data/processed/Conference_Article_Authors_Clean.csv";
    private static final String JOURNAL_ARTICLE_AUTHORS_OUT = "data/processed/Journal_Article_Authors_Clean.csv";

    // maps to give every author only one id
    private final Map<String, Integer> authorIdsByNormalizedName = new LinkedHashMap<>();   // map for checking duplicates
    private final Map<Integer, String> authorNamesById = new LinkedHashMap<>();             // map for writing the clean authors file

    // maps that keep the conferences found
    private final Map<String, Integer> conferenceIdsByAlias = new LinkedHashMap<>();        // map for finding conference by alias
    private final Map<Integer, Conference> conferencesById = new LinkedHashMap<>();         // map for finding conference by id

    // maps that keep the journals found
    private final Map<String, Integer> journalIdsByAlias = new LinkedHashMap<>();           // map for finding journal by alias
    private final Map<Integer, Journal> journalsById = new LinkedHashMap<>();               // map for finding journal by id

    // sets to avoid duplicate article-author pairs
    private final Set<String> seenConferenceArticleAuthorRelations = new LinkedHashSet<>(); // set for conference article-author duplicate relations
    private final Set<String> seenJournalArticleAuthorRelations = new LinkedHashSet<>();    // set for journal article-author duplicate relations

    // original_dblp_id -> generated article_id
    // used to avoid duplicate article rows while still allowing us to merge any additional author relations
    private final Map<String, Integer> conferenceArticleIdsByOriginalId = new HashMap<>();
    private final Map<String, Integer> journalArticleIdsByOriginalId = new HashMap<>();

    // these counters create new ids (database ids start from 1)
    private int nextAuthorId = 1;
    private int nextConferenceId = 1;
    private int nextJournalId = 1;
    private int nextConferenceArticleId = 1;
    private int nextJournalArticleId = 1;

    // write rows into the output files
    private BufferedWriter conferenceArticlesWriter;
    private BufferedWriter journalArticlesWriter;
    private BufferedWriter conferenceArticleAuthorsWriter;
    private BufferedWriter journalArticleAuthorsWriter;

    // main is where the program starts
    public static void main(String[] args) {
        String conferenceInput;
        String journalInput;

        // user-given file names
        // if not given, use the default project files
        if (args.length >= 1) {
            conferenceInput = args[0];
        } else {
            conferenceInput = DEFAULT_CONFERENCE_INPUT;
        }

        if (args.length >= 2) {
            journalInput = args[1];
        } else {
            journalInput = DEFAULT_JOURNAL_INPUT;
        }

        ETL etl = new ETL();
        try {
            // run the ETL using the selected input files
            etl.run(conferenceInput, journalInput);

            // print the files that were created
            System.out.println("\nETL completed successfully.");
            System.out.println("Generated load files:");
            System.out.println("- " + AUTHORS_OUT);
            System.out.println("- " + CONFERENCES_OUT);
            System.out.println("- " + JOURNALS_OUT);
            System.out.println("- " + CONFERENCE_ARTICLES_OUT);
            System.out.println("- " + JOURNAL_ARTICLES_OUT);
            System.out.println("- " + CONFERENCE_ARTICLE_AUTHORS_OUT);
            System.out.println("- " + JOURNAL_ARTICLE_AUTHORS_OUT);
        } catch (IOException e) {
            System.err.println("Fatal ETL error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // this method runs the whole ETL process
    // first it loads optional ranking files
    // then it reads the article files
    // at the end, it writes the lookup tables
    private void run(String conferenceInput, String journalInput) throws IOException {
        // load files before article processing so ranking data can be attached
        // to the same conference/journal ids used by articles
        loadOptionalConferenceRankings();
        loadOptionalJournalRankings();

        // article and relation outputs are very big
        // put them directly into disk instead of memory
        try (
            BufferedWriter confArticles = newUtf8Writer(CONFERENCE_ARTICLES_OUT);
            BufferedWriter journalArticles = newUtf8Writer(JOURNAL_ARTICLES_OUT);
            BufferedWriter confArticleAuthors = newUtf8Writer(CONFERENCE_ARTICLE_AUTHORS_OUT);
            BufferedWriter journalArticleAuthors = newUtf8Writer(JOURNAL_ARTICLE_AUTHORS_OUT)
        ) {
            // keep these writers so helper methods can use them
            this.conferenceArticlesWriter = confArticles;
            this.journalArticlesWriter = journalArticles;
            this.conferenceArticleAuthorsWriter = confArticleAuthors;
            this.journalArticleAuthorsWriter = journalArticleAuthors;

            // process both main article files
            System.out.println("Starting ETL for conferences: " + conferenceInput);
            processConferenceArticles(conferenceInput);

            System.out.println("\nStarting ETL for journals: " + journalInput);
            processJournalArticles(journalInput);
        }

        // lookup tables depend on all source rows because new authors,
        // conferences, and journals may be found anytime
        writeLookupTables();

        // print a small report at the end
        System.out.println("\nSummary");
        System.out.println("Unique authors: " + authorNamesById.size());
        System.out.println("Conferences: " + conferencesById.size());
        System.out.println("Journals: " + journalsById.size());
        System.out.println("Conference articles: " + conferenceArticleIdsByOriginalId.size());
        System.out.println("Journal articles: " + journalArticleIdsByOriginalId.size());
        System.out.println("Conference article-author relations: " + seenConferenceArticleAuthorRelations.size());
        System.out.println("Journal article-author relations: " + seenJournalArticleAuthorRelations.size());
    }

    // read the conference articles file
    // for every good row, it writes a clean article row and the author links
    // if a row has important missing data, it skips the row
    private void processConferenceArticles(String inputFile) throws IOException {
        // turn the file name into a path object
        Path inputPath = Paths.get(inputFile);
        if (!Files.exists(inputPath)) {
            System.err.println("File not found, skipping: " + inputFile);
            return;
        }

        // open the input file using UTF-8
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            // find what separates the columns in this file
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // find the positions of the columns that we need
            String idColumn = "id";
            String originalDblpIdColumn = "original_dblp_id";
            String dblpIdColumn = "dblp_id";
            String authorsColumn = "authors";
            String authorColumn = "author";
            String titleColumn = "title";
            String paperTitleColumn = "paper_title";
            String yearColumn = "year";
            String pagesColumn = "pages";
            String booktitleColumn = "booktitle";
            String conferenceColumn = "conference";
            String acronymColumn = "acronym";
            String venueColumn = "venue";

            int idIndex = requiredIndex(headerIndex, inputFile, idColumn, originalDblpIdColumn, dblpIdColumn);
            int authorsIndex = optionalIndex(headerIndex, authorsColumn, authorColumn);
            int titleIndex = requiredIndex(headerIndex, inputFile, titleColumn, paperTitleColumn);
            int yearIndex = requiredIndex(headerIndex, inputFile, yearColumn);
            int pagesIndex = optionalIndex(headerIndex, pagesColumn);
            int booktitleIndex = requiredIndex(headerIndex, inputFile, booktitleColumn, conferenceColumn, acronymColumn, venueColumn);

            String line;
            int processed = 0;

            while ((line = reader.readLine()) != null) {
                // split the current row into columns
                List<String> columns = splitDelimitedLine(line, delimiter);

                // get the values from this row
                String originalDblpId = valueAt(columns, idIndex);
                String title = valueAt(columns, titleIndex);
                String yearText = valueAt(columns, yearIndex);
                String pages = valueAt(columns, pagesIndex);
                String booktitle = valueAt(columns, booktitleIndex);
                String authors = valueAt(columns, authorsIndex);

                // if important values are missing -> skip this row
                if (isNullLike(originalDblpId)) {
                    continue;
                }
                if (isNullLike(title)) {
                    continue;
                }
                Integer year = parseYear(yearText);
                if (year == null) {
                    continue;
                }
                if (isNullLike(booktitle)) {
                    continue;
                }

                // create or reuse ids, then write the clean data
                int conferenceId = getOrCreateConference(booktitle);
                int articleId = getOrCreateConferenceArticle(originalDblpId, title, year, pages, conferenceId);
                writeAuthorRelations(authors, articleId, conferenceArticleAuthorsWriter, seenConferenceArticleAuthorRelations);
                processed++;
            }

            System.out.println("Processed conference rows: " + processed);
        }
    }

    // this method does the same thing as processConferenceArticles,
    // but for journal articles
    private void processJournalArticles(String inputFile) throws IOException {
        // turn the file name into a path object
        Path inputPath = Paths.get(inputFile);
        if (!Files.exists(inputPath)) {
            System.err.println("File not found, skipping: " + inputFile);
            return;
        }

        // open the input file using UTF-8
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            // find what separates the columns in this file
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // find the positions of the columns that we need
            String idColumn = "id";
            String originalDblpIdColumn = "original_dblp_id";
            String dblpIdColumn = "dblp_id";
            String authorsColumn = "authors";
            String authorColumn = "author";
            String titleColumn = "title";
            String paperTitleColumn = "paper_title";
            String yearColumn = "year";
            String pagesColumn = "pages";
            String volumeColumn = "volume";
            String volColumn = "vol";
            String journalColumn = "journal";
            String journalTitleColumn = "journal_title";
            String venueColumn = "venue";

            int idIndex = requiredIndex(headerIndex, inputFile, idColumn, originalDblpIdColumn, dblpIdColumn);
            int authorsIndex = optionalIndex(headerIndex, authorsColumn, authorColumn);
            int titleIndex = requiredIndex(headerIndex, inputFile, titleColumn, paperTitleColumn);
            int yearIndex = requiredIndex(headerIndex, inputFile, yearColumn);
            int pagesIndex = optionalIndex(headerIndex, pagesColumn);
            int volumeIndex = optionalIndex(headerIndex, volumeColumn, volColumn);
            int journalIndex = requiredIndex(headerIndex, inputFile, journalColumn, journalTitleColumn, venueColumn);

            String line;
            int processed = 0;

            while ((line = reader.readLine()) != null) {
                // split the current row into columns
                List<String> columns = splitDelimitedLine(line, delimiter);

                // get the values from this row
                String originalDblpId = valueAt(columns, idIndex);
                String title = valueAt(columns, titleIndex);
                String yearText = valueAt(columns, yearIndex);
                String pages = valueAt(columns, pagesIndex);
                String volume = valueAt(columns, volumeIndex);
                String journalTitle = valueAt(columns, journalIndex);
                String authors = valueAt(columns, authorsIndex);

                // if important values are missing -> skip this row
                if (isNullLike(originalDblpId)) {
                    continue;
                }
                if (isNullLike(title)) {
                    continue;
                }
                Integer year = parseYear(yearText);
                if (year == null) {
                    continue;
                }
                if (isNullLike(journalTitle)) {
                    continue;
                }

                // the journal may already exist from the ranking file
                // if not, a new journal id is created from the article row
                int journalId = getOrCreateJournal(journalTitle);
                int articleId = getOrCreateJournalArticle(originalDblpId, title, year, volume, pages, journalId);
                writeAuthorRelations(authors, articleId, journalArticleAuthorsWriter, seenJournalArticleAuthorRelations);
                processed++;
            }

            System.out.println("Processed journal rows: " + processed);
        }
    }

    // this method returns the id of a conference
    // if the conference does not exist yet, it creates it
    private int getOrCreateConference(String title) {
        // article rows may only have one conference value
        String acronym = title;
        String rankCategory = null;
        String primaryFor = null;

        // use the longer method with empty ranking data
        return getOrCreateConference(acronym, title, rankCategory, primaryFor);
    }

    private int getOrCreateConference(String acronym, String title, String rankCategory, String primaryFor) {
        // clean the two possible conference names
        String cleanedAcronym = cleanText(acronym);
        String cleanedTitle = cleanText(title);

        // prefer acronym for lookup when it exists
        String lookupValue;
        if (!isNullLike(cleanedAcronym)) {
            lookupValue = cleanedAcronym;
        } else {
            lookupValue = cleanedTitle;
        }

        String key = normalizeLookupKey(lookupValue);

        // check if this conference already exists
        Integer existingId = conferenceIdsByAlias.get(key);
        if (existingId != null) {
            Conference existing = conferencesById.get(existingId);
            // keep old data, but fill empty ranking fields if we find them later

            if (isNullLike(existing.rankCategory)) {
                if (!isNullLike(rankCategory)) {
                    existing.rankCategory = cleanText(rankCategory);
                }
            }
            if (isNullLike(existing.primaryFor)) {
                if (!isNullLike(primaryFor)) {
                    existing.primaryFor = cleanText(primaryFor);
                }
            }
            addConferenceAliases(existingId, cleanedAcronym, cleanedTitle);
            return existingId;
        }

        // if no conference matched, create a new conference_id
        // also save both the acronym and title for searching later
        int id = nextConferenceId++;
        String cleanedRankCategory = cleanText(rankCategory);
        String cleanedPrimaryFor = cleanText(primaryFor);

        Conference conference = new Conference(id, cleanedAcronym, cleanedTitle, cleanedRankCategory, cleanedPrimaryFor);
        conferencesById.put(id, conference);

        // save names that can find this conference later
        addConferenceAliases(id, cleanedAcronym, cleanedTitle);
        return id;
    }

    // this method returns the id of a journal
    // if the journal does not exist yet, it creates it
    private int getOrCreateJournal(String title) {
        // article rows only know the journal title
        String country = null;
        String sjrIndex = null;
        String bestQuartile = null;
        String totalDocs3y = null;
        String totalRefs = null;
        String citesPerDoc2y = null;

        // use the longer method with empty ranking data
        return getOrCreateJournal(title, country, sjrIndex, bestQuartile, totalDocs3y, totalRefs, citesPerDoc2y);
    }

    private int getOrCreateJournal(String title, String country, String sjrIndex, String bestQuartile,
                                   String totalDocs3y, String totalRefs, String citesPerDoc2y) {
        // clean the title before using it as a lookup key
        String cleanedTitle = cleanText(title);
        String key = normalizeLookupKey(cleanedTitle);

        // first try an exact normalized match
        Integer existingId = journalIdsByAlias.get(key);
        if (existingId == null) {
            // if exact match fails, try a simple similar-title match
            existingId = findSimilarJournalId(cleanedTitle);
        }

        if (existingId != null) {
            // reuse the existing journal_id
            // and fill empty ranking fields if possible
            Journal existing = journalsById.get(existingId);
            updateIfMissing(existing, country, sjrIndex, bestQuartile, totalDocs3y, totalRefs, citesPerDoc2y);
            addJournalAliases(existingId, cleanedTitle);
            return existingId;
        }

        // if the journal title is new, create a new journal id
        // clean the ranking numbers before saving them
        int id = nextJournalId++;
        String cleanedCountry = cleanText(country);
        String cleanedSjrIndex = cleanDecimal(sjrIndex);
        String cleanedBestQuartile = cleanText(bestQuartile);
        String cleanedTotalDocs3y = cleanInteger(totalDocs3y);
        String cleanedTotalRefs = cleanInteger(totalRefs);
        String cleanedCitesPerDoc2y = cleanDecimal(citesPerDoc2y);

        Journal journal = new Journal(
            id,
            cleanedTitle,
            cleanedCountry,
            cleanedSjrIndex,
            cleanedBestQuartile,
            cleanedTotalDocs3y,
            cleanedTotalRefs,
            cleanedCitesPerDoc2y
        );
        journalsById.put(id, journal);

        // save the title so later rows can find this journal
        addJournalAliases(id, cleanedTitle);
        return id;
    }

    // this only fills journal ranking fields that are still empty
    private void updateIfMissing(Journal existing, String country, String sjrIndex, String bestQuartile,
                                 String totalDocs3y, String totalRefs, String citesPerDoc2y) {
        // add country only if it was missing before
        if (isNullLike(existing.country)) {
            if (!isNullLike(country)) {
                String cleanedCountry = cleanText(country);
                existing.country = cleanedCountry;
            }
        }
        // add SJR only if it was missing before
        if (isNullLike(existing.sjrIndex)) {
            if (!isNullLike(sjrIndex)) {
                String cleanedSjrIndex = cleanDecimal(sjrIndex);
                existing.sjrIndex = cleanedSjrIndex;
            }
        }
        // add quartile only if it was missing before
        if (isNullLike(existing.bestQuartile)) {
            if (!isNullLike(bestQuartile)) {
                String cleanedBestQuartile = cleanText(bestQuartile);
                existing.bestQuartile = cleanedBestQuartile;
            }
        }
        // add document count only if it was missing before
        if (isNullLike(existing.totalDocs3y)) {
            if (!isNullLike(totalDocs3y)) {
                String cleanedTotalDocs3y = cleanInteger(totalDocs3y);
                existing.totalDocs3y = cleanedTotalDocs3y;
            }
        }
        // add reference count only if it was missing before
        if (isNullLike(existing.totalRefs)) {
            if (!isNullLike(totalRefs)) {
                String cleanedTotalRefs = cleanInteger(totalRefs);
                existing.totalRefs = cleanedTotalRefs;
            }
        }
        // add cites per document only if it was missing before
        if (isNullLike(existing.citesPerDoc2y)) {
            if (!isNullLike(citesPerDoc2y)) {
                String cleanedCitesPerDoc2y = cleanDecimal(citesPerDoc2y);
                existing.citesPerDoc2y = cleanedCitesPerDoc2y;
            }
        }
    }

    // this writes a conference article only the first time we see it
    // if we see the same DBLP id again, we reuse the old article id
    private int getOrCreateConferenceArticle(String originalDblpId, String title, int year, String pages, int conferenceId)
            throws IOException {
        // use the original DBLP id to avoid duplicate article rows
        String cleanedOriginalId = cleanText(originalDblpId);
        Integer existingId = conferenceArticleIdsByOriginalId.get(cleanedOriginalId);
        if (existingId != null) {
            return existingId;
        }

        int articleId = nextConferenceArticleId++;
        conferenceArticleIdsByOriginalId.put(cleanedOriginalId, articleId);

        // write the fields in the same order as the Conference_Articles table
        // article id, original DBLP id, title, year, pages, conference id
        // convert numbers to text before writing the row
        String articleIdText = String.valueOf(articleId);
        String cleanedTitle = cleanText(title);
        String yearText = String.valueOf(year);
        String cleanedPages = cleanText(pages);
        String conferenceIdText = String.valueOf(conferenceId);

        writeTsvLine(conferenceArticlesWriter,
            articleIdText,
            cleanedOriginalId,
            cleanedTitle,
            yearText,
            cleanedPages,
            conferenceIdText
        );

        return articleId;
    }

    // this writes a journal article only the first time we see it
    private int getOrCreateJournalArticle(String originalDblpId, String title, int year, String volume, String pages, int journalId)
            throws IOException {
        // use the original DBLP id to avoid duplicate article rows
        String cleanedOriginalId = cleanText(originalDblpId);
        Integer existingId = journalArticleIdsByOriginalId.get(cleanedOriginalId);
        if (existingId != null) {
            return existingId;
        }

        int articleId = nextJournalArticleId++;
        journalArticleIdsByOriginalId.put(cleanedOriginalId, articleId);

        // write the fields in the same order as the Journal_Articles table
        // article id, original DBLP id, title, year, volume, pages, journal id
        // convert numbers to text before writing the row
        String articleIdText = String.valueOf(articleId);
        String cleanedTitle = cleanText(title);
        String yearText = String.valueOf(year);
        String cleanedVolume = cleanText(volume);
        String cleanedPages = cleanText(pages);
        String journalIdText = String.valueOf(journalId);

        writeTsvLine(journalArticlesWriter,
            articleIdText,
            cleanedOriginalId,
            cleanedTitle,
            yearText,
            cleanedVolume,
            cleanedPages,
            journalIdText
        );

        return articleId;
    }

    // this takes the authors from one article row and writes the article-author rows
    private void writeAuthorRelations(String authorsValue, int articleId, BufferedWriter relationWriter, Set<String> seenRelations)
            throws IOException {
        // if the article has no authors, there is nothing to write
        if (isNullLike(authorsValue)) {
            return;
        }

        // in the input file, many authors are stored in one cell using | between them
        String authorSeparator = "\\|";
        String[] authors = authorsValue.split(authorSeparator);
        for (String authorName : authors) {
            // clean each author name before using it
            String cleanedAuthorName = cleanText(authorName);
            if (isNullLike(cleanedAuthorName)) {
                continue;
            }

            int authorId = getOrCreateAuthor(cleanedAuthorName);
            String relationSeparator = "-";
            String relationKey = articleId + relationSeparator + authorId;
            // if this relation is new, write it. If it already exists, skip it
            if (seenRelations.add(relationKey)) {
                String articleIdText = String.valueOf(articleId);
                String authorIdText = String.valueOf(authorId);

                writeTsvLine(relationWriter, articleIdText, authorIdText);
            }
        }
    }

    // this returns the id of an author
    // if the author is new, we create a new id
    private int getOrCreateAuthor(String authorName) {
        // normalize the name so duplicates are easier to find
        String normalizedAuthorName = normalizeAuthorName(authorName);
        Integer existingId = authorIdsByNormalizedName.get(normalizedAuthorName);
        if (existingId != null) {
            return existingId;
        }

        // create a new author id
        int id = nextAuthorId++;
        authorIdsByNormalizedName.put(normalizedAuthorName, id);
        authorNamesById.put(id, authorName);
        return id;
    }

    // this tries to find a conference ranking CSV file
    // if the file exists, we load extra conference information from it
    private void loadOptionalConferenceRankings() {
        // possible conference ranking file names
        String icoreKilledColumnsFile = "data/raw/iCore26_KilledColumnsForLoading.csv";
        String icoreKilledColumnsFolderFile = "data/raw/icore26_data/iCore26_KilledColumnsForLoading.csv";
        String icoreKilledColumnsLowercaseFolderFile = "data/raw/icore26_data/icore26_KilledColumnsForLoading.csv";
        String icoreCsvFile = "data/raw/iCore26.csv";
        String icoreCsvFolderFile = "data/raw/icore26_data/iCore26.csv";
        String icoreLowercaseCsvFolderFile = "data/raw/icore26_data/icore26.csv";
        String icoreRawFile = "data/raw/iCORE_raw.csv";
        String icoreRawFolderFile = "data/raw/icore26_data/iCORE_raw.csv";

        List<String> possibleFiles = Arrays.asList(
            icoreKilledColumnsFile,
            icoreKilledColumnsFolderFile,
            icoreKilledColumnsLowercaseFolderFile,
            icoreCsvFile,
            icoreCsvFolderFile,
            icoreLowercaseCsvFolderFile,
            icoreRawFile,
            icoreRawFolderFile
        );

        for (String file : possibleFiles) {
            // skip file names that do not exist
            Path path = Paths.get(file);
            if (!Files.exists(path)) {
                continue;
            }

            try {
                // stop after the first ranking file that works
                // so we do not load the same ranking data twice
                int loaded = loadConferenceRankingCsv(path);
                System.out.println("Loaded conference ranking rows from " + file + ": " + loaded);
                return;
            } catch (IOException e) {
                System.err.println("Could not load conference ranking file " + file + ": " + e.getMessage());
            }
        }

        String icoreExcelFile = "data/raw/icoreCategories.xlsx";
        String icoreExcelFolderFile = "data/raw/icore26_data/icoreCategories.xlsx";

        List<String> excelFiles = Arrays.asList(icoreExcelFile, icoreExcelFolderFile);

        // explain Excel files instead of trying to read them
        for (String excelFile : excelFiles) {
            Path excelPath = Paths.get(excelFile);
            if (Files.exists(excelPath)) {
                System.out.println("Found " + excelFile + ", but this ETL uses only standard Java and reads CSV files. "
                    + "Export the Excel sheet as CSV if you want the conference ranking fields enriched automatically.");
                return;
            }
        }
    }

    // this reads the conference ranking CSV file
    private int loadConferenceRankingCsv(Path path) throws IOException {
        // open the ranking file
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return 0;
            }

            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // these columns are optional because ranking files may be different
            // so the code accepts different possible column names
            String titleColumn = "title";
            String conferenceTitleColumn = "conference_title";
            String nameColumn = "name";
            String acronymColumn = "acronym";
            String abbrColumn = "abbr";
            String abbreviationColumn = "abbreviation";
            String rankColumn = "rank";
            String rankCategoryColumn = "rank_category";
            String rankingColumn = "ranking";
            String primaryForColumn = "primaryfor";
            String primaryForWithUnderscoreColumn = "primary_for";
            String primaryForCodeColumn = "primary_for_code";

            int titleIndex = optionalIndex(headerIndex, titleColumn, conferenceTitleColumn, nameColumn);
            int acronymIndex = optionalIndex(headerIndex, acronymColumn, abbrColumn, abbreviationColumn);
            int rankIndex = optionalIndex(headerIndex, rankColumn, rankCategoryColumn, rankingColumn);
            int primaryForIndex = optionalIndex(headerIndex, primaryForColumn, primaryForWithUnderscoreColumn, primaryForCodeColumn);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                // read the ranking row values
                List<String> columns = splitDelimitedLine(line, delimiter);
                String title = valueAt(columns, titleIndex);
                String acronym = valueAt(columns, acronymIndex);
                String rank = valueAt(columns, rankIndex);
                String primaryFor = valueAt(columns, primaryForIndex);

                if (isNullLike(title)) {
                    if (isNullLike(acronym)) {
                        continue;
                    }
                }

                // use the acronym when it exists
                // but keep the title too so articles can match either value
                String conferenceTitle;
                if (isNullLike(title)) {
                    conferenceTitle = acronym;
                } else {
                    conferenceTitle = title;
                }

                getOrCreateConference(acronym, conferenceTitle, rank, primaryFor);
                count++;
            }
            // return how many ranking rows were loaded
            return count;
        }
    }

    // this tries to find a journal ranking CSV file
    // if the file exists, we load extra journal information from it
    private void loadOptionalJournalRankings() {
        // possible journal ranking file names
        String journalRankingFile = "data/raw/journal_ranking_data_raw.csv";
        String journalRankingFolderFile = "data/raw/journal_ranking_data_raw/journal_ranking_data_raw.csv";

        List<String> possibleFiles = Arrays.asList(journalRankingFile, journalRankingFolderFile);

        for (String file : possibleFiles) {
            // skip file names that do not exist
            Path path = Paths.get(file);
            if (!Files.exists(path)) {
                continue;
            }

            try {
                // one ranking file is enough
                // loading two could duplicate the same journal data
                int loaded = loadJournalRankingCsv(path);
                System.out.println("Loaded journal ranking rows from " + file + ": " + loaded);
                return;
            } catch (IOException e) {
                System.err.println("Could not load journal ranking file " + file + ": " + e.getMessage());
            }
        }
    }

    // this reads the journal ranking CSV file
    private int loadJournalRankingCsv(Path path) throws IOException {
        // open the ranking file
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return 0;
            }

            char delimiter = detectDelimiter(headerLine);
            List<String> headers = splitDelimitedLine(headerLine, delimiter);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            // accept different column names that may appear in journal ranking files

            String titleColumn = "title";
            String journalTitleColumn = "journal_title";
            String sourceTitleColumn = "source_title";
            String journalColumn = "journal";
            String countryColumn = "country";
            String sjrIndexColumn = "sjrindex";
            String sjrIndexWithUnderscoreColumn = "sjr_index";
            String sjrColumn = "sjr";
            String bestQuartileColumn = "bestquartile";
            String bestQuartileWithUnderscoreColumn = "best_quartile";
            String quartileColumn = "quartile";
            String totalDocs3yColumn = "totaldocs3y";
            String totalDocs3yWithUnderscoreColumn = "total_docs_3y";
            String citableDocs3yColumn = "citabledocs3y";
            String citableDocs3yWithUnderscoreColumn = "citable_docs_3y";
            String totalRefsColumn = "totalrefs";
            String totalRefsWithUnderscoreColumn = "total_refs";
            String citesDoc2yColumn = "citesdoc2y";
            String citesPerDoc2yWithUnderscoreColumn = "cites_per_doc_2y";
            String citesPerDoc2yColumn = "citesperdoc2y";

            int titleIndex = optionalIndex(headerIndex, titleColumn, journalTitleColumn, sourceTitleColumn, journalColumn);
            int countryIndex = optionalIndex(headerIndex, countryColumn);
            int sjrIndex = optionalIndex(headerIndex, sjrIndexColumn, sjrIndexWithUnderscoreColumn, sjrColumn);
            int bestQuartileIndex = optionalIndex(headerIndex, bestQuartileColumn, bestQuartileWithUnderscoreColumn, quartileColumn);
            int totalDocs3yIndex = optionalIndex(headerIndex, totalDocs3yColumn, totalDocs3yWithUnderscoreColumn, citableDocs3yColumn, citableDocs3yWithUnderscoreColumn);
            int totalRefsIndex = optionalIndex(headerIndex, totalRefsColumn, totalRefsWithUnderscoreColumn);
            int citesPerDoc2yIndex = optionalIndex(headerIndex, citesDoc2yColumn, citesPerDoc2yWithUnderscoreColumn, citesPerDoc2yColumn);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                // read the ranking row values
                List<String> columns = splitDelimitedLine(line, delimiter);
                String title = valueAt(columns, titleIndex);
                if (isNullLike(title)) {
                    continue;
                }

                String country = valueAt(columns, countryIndex);
                String sjr = valueAt(columns, sjrIndex);
                String bestQuartile = valueAt(columns, bestQuartileIndex);
                String totalDocs3y = valueAt(columns, totalDocs3yIndex);
                String totalRefs = valueAt(columns, totalRefsIndex);
                String citesPerDoc2y = valueAt(columns, citesPerDoc2yIndex);

                getOrCreateJournal(
                    title,
                    country,
                    sjr,
                    bestQuartile,
                    totalDocs3y,
                    totalRefs,
                    citesPerDoc2y
                );
                count++;
            }
            // return how many ranking rows were loaded
            return count;
        }
    }

    // this writes the final Authors, Conferences, and Journals files
    private void writeLookupTables() throws IOException {
        // write the authors lookup table
        try (BufferedWriter writer = newUtf8Writer(AUTHORS_OUT)) {
            for (Map.Entry<Integer, String> entry : authorNamesById.entrySet()) {
                String authorIdText = String.valueOf(entry.getKey());
                String authorName = entry.getValue();

                writeTsvLine(writer, authorIdText, authorName);
            }
        }

        // write the conferences lookup table
        try (BufferedWriter writer = newUtf8Writer(CONFERENCES_OUT)) {
            for (Conference conference : conferencesById.values()) {
                String conferenceIdText = String.valueOf(conference.id);
                String cleanedPrimaryFor = cleanInteger(conference.primaryFor);

                writeTsvLine(writer,
                    conferenceIdText,
                    conference.acronym,
                    conference.title,
                    conference.rankCategory,
                    cleanedPrimaryFor
                );
            }
        }

        // write the journals lookup table
        try (BufferedWriter writer = newUtf8Writer(JOURNALS_OUT)) {
            for (Journal journal : journalsById.values()) {
                String journalIdText = String.valueOf(journal.id);

                writeTsvLine(writer,
                    journalIdText,
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

    // this stores all names that can point to the same conference
    private void addConferenceAliases(int conferenceId, String acronym, String title) {
        addAlias(conferenceIdsByAlias, acronym, conferenceId);
        addAlias(conferenceIdsByAlias, title, conferenceId);
    }

    // this stores the journal title as a name for this journal id
    private void addJournalAliases(int journalId, String title) {
        addAlias(journalIdsByAlias, title, journalId);
    }

    // this adds one lookup name to a map
    private void addAlias(Map<String, Integer> aliases, String value, int id) {
        // ignore empty alias values
        if (isNullLike(value)) {
            return;
        }

        // normalize the alias before saving it
        String key = normalizeLookupKey(value);
        if (!aliases.containsKey(key)) {
            aliases.put(key, id);
        }
    }

    // this tries to find a journal with a very similar title
    private Integer findSimilarJournalId(String journalTitle) {
        // normalize the requested journal title
        String requested = normalizeLookupKey(journalTitle);
        if (requested.length() < 8) {
            return null;
        }

        // compare the requested title with existing journal aliases
        for (Map.Entry<String, Integer> entry : journalIdsByAlias.entrySet()) {
            String existing = entry.getKey();
            if (existing.length() < 8) {
                continue;
            }
            if (existing.contains(requested)) {
                return entry.getValue();
            }
            if (requested.contains(existing)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // this opens a file for writing and creates the folder if needed
    private static BufferedWriter newUtf8Writer(String outputFile) throws IOException {
        // create the parent folder before opening the file
        Path outputPath = Paths.get(outputFile);
        Path outputFolder = outputPath.getParent();

        if (outputFolder != null) {
            Files.createDirectories(outputFolder);
        }

        return Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
    }

    // this writes one line with tabs between the values
    private static void writeTsvLine(BufferedWriter writer, String... values) throws IOException {
        char tabCharacter = '\t';

        // write each value with a tab between values
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(tabCharacter);
            }
            String originalValue = values[i];
            String safeValue = toTsvField(originalValue);
            writer.write(safeValue);
        }
        // finish the row
        writer.newLine();
    }

    // this makes one value safe for a TSV file
    private static String toTsvField(String value) {
        // database null is written as \N
        if (isNullLike(value)) {
            String databaseNullValue = "\\N";
            return databaseNullValue;
        }
        char tabCharacter = '\t';
        char carriageReturnCharacter = '\r';
        char newLineCharacter = '\n';
        char spaceCharacter = ' ';
        String spaceText = " ";

        String noTabs = value.replace(tabCharacter, spaceCharacter);
        String noCarriageReturns = noTabs.replace(carriageReturnCharacter, spaceCharacter);
        String noNewLines = noCarriageReturns.replace(newLineCharacter, spaceCharacter);
        String manySpacesPattern = "\\s+";

        // collapse repeated spaces into one space
        String singleSpaces = noNewLines.replaceAll(manySpacesPattern, spaceText);
        String trimmed = singleSpaces.trim();

        return trimmed;
    }

    // this creates a map so we can find columns by their header names
    private static Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();

        // save each header name with its column position
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String normalizedHeader = normalizeHeader(header);
            index.put(normalizedHeader, i);
        }
        return index;
    }

    // this finds a required column. If it is missing, the program stops with an error
    private static int requiredIndex(Map<String, Integer> headerIndex, String fileName, String... candidates) {
        // first try to find it like an optional column
        int index = optionalIndex(headerIndex, candidates);
        if (index < 0) {
            // stop the program if a required column is missing
            String candidateNames = Arrays.toString(candidates);
            String errorMessage = "Missing required column in " + fileName + ": one of " + candidateNames;

            throw new IllegalArgumentException(errorMessage);
        }
        return index;
    }

    // this finds an optional column. If it does not exist, it returns -1
    private static int optionalIndex(Map<String, Integer> headerIndex, String... candidates) {
        // try each possible column name
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeHeader(candidate);
            Integer index = headerIndex.get(normalizedCandidate);
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    // this safely gets one value from a row
    private static String valueAt(List<String> values, int index) {
        // missing optional columns use -1
        if (index < 0) {
            return null;
        }
        // short rows may not have every column
        if (index >= values.size()) {
            return null;
        }

        // clean the value before returning it
        String value = values.get(index);
        String cleanedValue = cleanText(value);

        return cleanedValue;
    }

    // this guesses if the file uses comma, semicolon, or tab
    private static char detectDelimiter(String headerLine) {
        // test the common delimiters
        char semicolon = ';';
        char comma = ',';
        char tab = '\t';
        char[] candidates = new char[] {semicolon, comma, tab};
        char best = semicolon;
        int bestCount = -1;

        for (char candidate : candidates) {
            // count delimiters that are not inside quotes
            int count = countDelimiterOutsideQuotes(headerLine, candidate);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    // this counts delimiters, but ignores delimiters inside quotes
    private static int countDelimiterOutsideQuotes(String line, char delimiter) {
        boolean inQuotes = false;
        int count = 0;
        char quoteCharacter = '"';

        for (int i = 0; i < line.length(); i++) {
            // check the current character
            char c = line.charAt(i);
            boolean currentCharacterIsQuote = c == quoteCharacter;
            boolean hasNextCharacter = i + 1 < line.length();
            boolean nextCharacterIsQuote = hasNextCharacter && line.charAt(i + 1) == quoteCharacter;
            boolean foundEscapedQuote = inQuotes && nextCharacterIsQuote;
            boolean foundDelimiterOutsideQuotes = c == delimiter && !inQuotes;

            if (currentCharacterIsQuote) {
                if (foundEscapedQuote) {
                    // two quotes inside quotes mean one real quote
                    i++;
                } else {
                    // entering or leaving a quoted value
                    inQuotes = !inQuotes;
                }
            } else if (foundDelimiterOutsideQuotes) {
                // count only real separators
                count++;
            }
        }
        return count;
    }

    // this splits one CSV/TSV line into columns
    private static List<String> splitDelimitedLine(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteCharacter = '"';
        int emptyLength = 0;

        for (int i = 0; i < line.length(); i++) {
            // check the current character
            char c = line.charAt(i);
            boolean currentCharacterIsQuote = c == quoteCharacter;
            boolean hasNextCharacter = i + 1 < line.length();
            boolean nextCharacterIsQuote = hasNextCharacter && line.charAt(i + 1) == quoteCharacter;
            boolean foundEscapedQuote = inQuotes && nextCharacterIsQuote;
            boolean foundDelimiterOutsideQuotes = c == delimiter && !inQuotes;

            if (currentCharacterIsQuote) {
                if (foundEscapedQuote) {
                    // two quotes inside quotes mean one real quote
                    current.append(quoteCharacter);
                    i++;
                } else {
                    // entering or leaving a quoted value
                    inQuotes = !inQuotes;
                }
            } else if (foundDelimiterOutsideQuotes) {
                // finish the current column
                result.add(current.toString());
                current.setLength(emptyLength);
            } else {
                // keep building the current column
                current.append(c);
            }
        }
        // add the final column after the loop ends
        result.add(current.toString());
        return result;
    }

    // this makes header names easier to compare
    private static String normalizeHeader(String value) {
        // missing header names become empty text
        if (value == null) {
            String emptyText = "";
            return emptyText;
        }

        // remove BOM, lowercase, and remove punctuation
        String byteOrderMark = "\uFEFF";
        String emptyText = "";
        String withoutBom = value.replace(byteOrderMark, emptyText);
        String lowerCase = withoutBom.toLowerCase(Locale.ROOT);
        String notALetterOrNumberPattern = "[^a-z0-9]";
        String lettersAndNumbersOnly = lowerCase.replaceAll(notALetterOrNumberPattern, emptyText);

        return lettersAndNumbersOnly;
    }

    // this cleans normal text values
    private static String cleanText(String value) {
        // null stays null
        if (value == null) {
            return null;
        }

        // remove BOM and extra whitespace
        String byteOrderMark = "\uFEFF";
        String emptyText = "";
        String spaceText = " ";
        String withoutBom = value.replace(byteOrderMark, emptyText);
        String trimmed = withoutBom.trim();
        String manySpacesPattern = "\\s+";
        String cleaned = trimmed.replaceAll(manySpacesPattern, spaceText);

        // convert empty-looking values to null
        if (isNullLike(cleaned)) {
            return null;
        }

        return cleaned;
    }

    // this checks if a value should be treated like missing data
    private static boolean isNullLike(String value) {
        // Java null is missing data
        if (value == null) {
            return true;
        }

        // compare common missing-value spellings
        String trimmed = value.trim();
        boolean emptyText = trimmed.isEmpty();
        String nullWord = "NULL";
        String notAvailableWord = "N/A";
        String shortNotAvailableWord = "NA";
        String databaseNullValue = "\\N";
        String dashValue = "-";

        boolean nullText = trimmed.equalsIgnoreCase(nullWord);
        boolean notAvailableText = trimmed.equalsIgnoreCase(notAvailableWord);
        boolean shortNotAvailableText = trimmed.equalsIgnoreCase(shortNotAvailableWord);
        boolean databaseNullText = trimmed.equals(databaseNullValue);
        boolean dashText = trimmed.equals(dashValue);

        // if any missing-value check is true, the value is missing
        return emptyText
            || nullText
            || notAvailableText
            || shortNotAvailableText
            || databaseNullText
            || dashText;
    }

    // this tries to get a 4-digit year from a value
    private static Integer parseYear(String value) {
        // missing year cannot be parsed
        if (isNullLike(value)) {
            return null;
        }

        // accept a clean four digit year
        String trimmed = value.trim();
        String fourDigitYearPattern = "\\d{4}";
        if (trimmed.matches(fourDigitYearPattern)) {
            return Integer.valueOf(trimmed);
        }

        // otherwise try to find a four digit year inside the text
        String firstFourDigitYearPattern = ".*?(\\d{4}).*";
        String firstMatchReplacement = "$1";
        String firstYearFound = trimmed.replaceAll(firstFourDigitYearPattern, firstMatchReplacement);
        if (firstYearFound.matches(fourDigitYearPattern)) {
            return Integer.valueOf(firstYearFound);
        }
        return null;
    }

    // this cleans a value that should be an integer
    private static String cleanInteger(String value) {
        // missing values stay null
        if (isNullLike(value)) {
            return null;
        }

        // remove commas before checking the number
        String commaText = ",";
        String emptyText = "";
        String trimmed = value.trim().replace(commaText, emptyText);
        String integerPattern = "-?\\d+";
        String decimalEndingInZeroPattern = "-?\\d+\\.0+";

        if (trimmed.matches(integerPattern)) {
            return trimmed;
        }

        // convert values like 10.0 into 10
        if (trimmed.matches(decimalEndingInZeroPattern)) {
            char decimalPointCharacter = '.';
            int decimalPointIndex = trimmed.indexOf(decimalPointCharacter);
            int startOfNumber = 0;
            String numberBeforeDecimalPoint = trimmed.substring(startOfNumber, decimalPointIndex);

            return numberBeforeDecimalPoint;
        }
        return null;
    }

    // this cleans a value that should be a decimal number
    private static String cleanDecimal(String value) {
        // missing values stay null
        if (isNullLike(value)) {
            return null;
        }

        // change comma decimals into dot decimals
        String commaText = ",";
        String decimalPointText = ".";
        String trimmed = value.trim().replace(commaText, decimalPointText);
        String decimalPattern = "-?\\d+(\\.\\d+)?";

        if (trimmed.matches(decimalPattern)) {
            return trimmed;
        }
        return null;
    }

    // this creates a simple key for matching conferences and journals
    private static String normalizeLookupKey(String value) {
        // missing values use an empty key
        if (isNullLike(value)) {
            String emptyText = "";
            return emptyText;
        }

        // start with the same cleaning used for author names
        String normalized = normalizeAuthorName(value);
        String nonLetterOrNumberPattern = "[^a-z0-9]+";
        String wordsToIgnorePattern = "\\b(the|of|and|for|on|in|journal|transactions|proceedings|conference|international)\\b";
        String manySpacesPattern = "\\s+";
        String ampersandText = "&";
        String andText = "and";
        String spaceText = " ";

        // remove punctuation and common venue words
        normalized = normalized.replace(ampersandText, andText);
        normalized = normalized.replaceAll(nonLetterOrNumberPattern, spaceText);
        normalized = normalized.replaceAll(wordsToIgnorePattern, spaceText);
        normalized = normalized.replaceAll(manySpacesPattern, spaceText).trim();
        return normalized;
    }

    // this creates a simple version of an author name for duplicate checking
    private static String normalizeAuthorName(String name) {
        // compare names in lowercase
        String normalized = name.toLowerCase(Locale.ROOT);

        // replace some special letters with simpler English letters first
        String sharpS = "ß";
        String sharpSReplacement = "ss";
        String aeLetter = "æ";
        String aeReplacement = "ae";
        String oeLetter = "œ";
        String oeReplacement = "oe";
        String oSlashLetter = "ø";
        String oSlashReplacement = "o";
        String dStrokeLetter = "đ";
        String dStrokeReplacement = "d";
        String lStrokeLetter = "ł";
        String lStrokeReplacement = "l";

        normalized = normalized.replace(sharpS, sharpSReplacement);
        normalized = normalized.replace(aeLetter, aeReplacement);
        normalized = normalized.replace(oeLetter, oeReplacement);
        normalized = normalized.replace(oSlashLetter, oSlashReplacement);
        normalized = normalized.replace(dStrokeLetter, dStrokeReplacement);
        normalized = normalized.replace(lStrokeLetter, lStrokeReplacement);

        // split accented letters into base letter + accent mark
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        String accentMarkPattern = "\\p{M}";
        String manySpacesPattern = "\\s+";
        String emptyText = "";
        String spaceText = " ";

        // remove accents and extra spaces
        normalized = normalized.replaceAll(accentMarkPattern, emptyText);
        normalized = normalized.replaceAll(manySpacesPattern, spaceText);

        return normalized.trim();
    }

    // small class used only to keep conference data while the program runs
    private static final class Conference {
        private final int id;
        private final String acronym;
        private final String title;
        private String rankCategory;
        private String primaryFor;

        private Conference(int id, String acronym, String title, String rankCategory, String primaryFor) {
            // save the conference fields
            this.id = id;
            this.acronym = acronym;

            // use acronym as the title if no title exists
            String finalTitle;
            if (isNullLike(title)) {
                finalTitle = acronym;
            } else {
                finalTitle = title;
            }
            this.title = finalTitle;
            this.rankCategory = rankCategory;
            this.primaryFor = primaryFor;
        }
    }

    // small class used only to keep journal data while the program runs
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
            // save the journal fields
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
