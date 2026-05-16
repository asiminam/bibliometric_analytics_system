package src;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// this class creates the dashboard window
public class Dashboard {

    // database connection information
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/bookdata_4991?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";
    private static final String DB_USER = "root";

    // colors used by the dashboard UI
    private static final Color PAGE_BACKGROUND = new Color(244, 247, 251);
    private static final Color SURFACE_COLOR = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(216, 224, 235);
    private static final Color TEXT_COLOR = new Color(31, 41, 55);
    private static final Color MUTED_TEXT_COLOR = new Color(75, 85, 99);
    private static final Color PRIMARY_COLOR = new Color(37, 99, 180);
    private static final Color SELECTION_COLOR = new Color(219, 234, 254);
    private static final Color CHART_GRID_COLOR = new Color(203, 213, 225);
    private static final Color CHART_ACCENT_COLOR = new Color(37, 99, 180);

    // fonts used by the dashboard UI
    private static final Font BASE_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font BOLD_FONT = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 14);

    // this connection is used by all queries
    private Connection connection;

    // this starts the dashboard program
    public static void main(String[] args) {
        // start the Swing window on the Swing thread
        SwingUtilities.invokeLater(() -> {
            try {
                // create and start the dashboard
                new Dashboard().start();
            } catch (Exception e) {
                // show an error if the app cannot open
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Could not start application:\n" + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    // this connects to the database and opens the main window
    private void start() throws Exception {
        // make Swing use the polished dashboard style
        applyPolishedLookAndFeel();

        // ask the user for the database password
        Component parentWindow = null; // null means the popup has no parent window
        String passwordMessage = "Enter MySQL root password:"; // message shown inside the popup
        String passwordWindowTitle = "Database Login"; // title shown at the top of the popup

        String password = JOptionPane.showInputDialog(
                parentWindow,
                passwordMessage,
                passwordWindowTitle,
                JOptionPane.QUESTION_MESSAGE
        );

        if (password == null) {
            return;
        }

        connection = DriverManager.getConnection(DB_URL, DB_USER, password); // connect to MySQL

        // create the main window
        JFrame frame = new JFrame("Bibliometric Analytics System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // close the app when the window closes
        frame.setSize(1100, 700); // set the starting window size
        frame.setMinimumSize(new Dimension(980, 640)); // stop the window from becoming too small
        frame.setLocationRelativeTo(null); // center the window on the screen
        frame.getContentPane().setBackground(PAGE_BACKGROUND); // set the window background color

        JTabbedPane tabs = new JTabbedPane(); // create all dashboard tabs

        tabs.addTab("Database Summary", createSummaryPanel()); // add the database summary tab
        tabs.addTab("Conference Yearly Stats", createConferencePanel()); // add the conference yearly statistics tab
        tabs.addTab("Journal Yearly Stats", createJournalPanel()); // add the journal yearly statistics tab
        tabs.addTab("Author Search", createAuthorPanel()); // add the author search tab
        tabs.addTab("Year Profile", createYearProfilePanel()); // add the year profile tab
        tabs.addTab("Publication Details", createPublicationDetailsPanel()); // add the publication details tab
        tabs.addTab("Venue Profile", createVenueProfilePanel()); // add the venue profile tab
        tabs.addTab("Author Profile", createAuthorProfilePanel()); // add the author profile tab
        tabs.addTab("Top Analytics", createTopAnalyticsPanel()); // add the top analytics tab

        polishComponentTree(tabs); // apply the same polish to every component

        frame.add(tabs); // add the tabs to the window
        frame.setVisible(true); // show the window
    }

    // this creates the database summary tab
    private JPanel createSummaryPanel() {
        // create the panel and table
        JPanel panel = new JPanel(new BorderLayout());

        JTable table = new JTable();
        JButton loadButton = new JButton("Load Summary");

        loadButton.addActionListener(e -> {
            try {
                // count rows from the main database tables
                table.setModel(runQuery("""
                        SELECT 'Authors' AS table_name, COUNT(*) AS total_rows FROM Authors
                        UNION ALL
                        SELECT 'Conferences', COUNT(*) FROM Conferences
                        UNION ALL
                        SELECT 'Journals', COUNT(*) FROM Journals
                        UNION ALL
                        SELECT 'Conference Articles', COUNT(*) FROM Conference_Articles
                        UNION ALL
                        SELECT 'Journal Articles', COUNT(*) FROM Journal_Articles
                        UNION ALL
                        SELECT 'Conference Article-Author Relations', COUNT(*) FROM Conference_Article_Authors
                        UNION ALL
                        SELECT 'Journal Article-Author Relations', COUNT(*) FROM Journal_Article_Authors
                        """));
            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(loadButton, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // load the summary when the tab opens
        loadButton.doClick();

        return panel;
    }

    // this creates the conference yearly statistics tab
    private JPanel createConferencePanel() {
        // create the main panel
        JPanel panel = new JPanel(new BorderLayout());

        // create the search area
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField("EDBT", 25);
        JButton searchButton = new JButton("Search Conference");

        topPanel.add(new JLabel("Conference acronym/title:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        JTable table = new JTable();

        // create charts for the yearly result
        SimpleBarChart barChart = new SimpleBarChart("Conference articles per year - Bar Chart");
        SimpleLineChart lineChart = new SimpleLineChart("Conference articles per year - Line Chart");
        SimpleScatterChart scatterChart = new SimpleScatterChart("Conference articles per year - Scatter Plot");

        // put the charts next to each other
        JPanel chartsPanel = createChartRow(barChart, lineChart, scatterChart);

        // put the table above the charts
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table),
                chartsPanel
        );
        splitPane.setResizeWeight(0.55);

        searchButton.addActionListener(e -> {
            try {
                // use LIKE so partial conference names can match
                String pattern = "%" + searchField.getText().trim() + "%";

                // load yearly article counts for the matching conference
                DefaultTableModel model = runPreparedQuery("""
                        SELECT
                            c.conf_id,
                            c.acronym,
                            c.title,
                            ca.year,
                            COUNT(*) AS article_count
                        FROM Conferences c
                        JOIN Conference_Articles ca ON ca.conf_id = c.conf_id
                        WHERE c.acronym LIKE ? OR c.title LIKE ?
                        GROUP BY c.conf_id, c.acronym, c.title, ca.year
                        ORDER BY ca.year
                        LIMIT 1000
                        """, pattern, pattern);

                // show the data in the table and charts
                table.setModel(model);
                barChart.updateFromTable(model, 3, 4);
                lineChart.updateFromTable(model, 3, 4);
                scatterChart.updateFromTable(model, 3, 4);

            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);

        // run the default search when the tab opens
        searchButton.doClick();

        return panel;
    }

    // this creates the journal yearly statistics tab
    private JPanel createJournalPanel() {
        // create the main panel
        JPanel panel = new JPanel(new BorderLayout());

        // create the search area
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField("Information Systems", 25);
        JButton searchButton = new JButton("Search Journal");

        topPanel.add(new JLabel("Journal title:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        JTable table = new JTable();

        // create charts for the yearly result
        SimpleBarChart barChart = new SimpleBarChart("Journal articles per year - Bar Chart");
        SimpleLineChart lineChart = new SimpleLineChart("Journal articles per year - Line Chart");
        SimpleScatterChart scatterChart = new SimpleScatterChart("Journal articles per year - Scatter Plot");

        // put the charts next to each other
        JPanel chartsPanel = createChartRow(barChart, lineChart, scatterChart);

        // put the table above the charts
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table),
                chartsPanel
        );
        splitPane.setResizeWeight(0.55);

        searchButton.addActionListener(e -> {
            try {
                // use LIKE so partial journal names can match
                String pattern = "%" + searchField.getText().trim() + "%";

                // load yearly article counts for the matching journal
                DefaultTableModel model = runPreparedQuery("""
                        SELECT
                            j.journal_id,
                            j.title,
                            ja.year,
                            COUNT(*) AS article_count
                        FROM Journals j
                        JOIN Journal_Articles ja ON ja.journal_id = j.journal_id
                        WHERE j.title LIKE ?
                        GROUP BY j.journal_id, j.title, ja.year
                        ORDER BY ja.year
                        LIMIT 1000
                        """, pattern);

                // show the data in the table and charts
                table.setModel(model);
                barChart.updateFromTable(model, 2, 3);
                lineChart.updateFromTable(model, 2, 3);
                scatterChart.updateFromTable(model, 2, 3);

            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);

        // run the default search when the tab opens
        searchButton.doClick();

        return panel;
    }

    // this creates the author search tab
    private JPanel createAuthorPanel() {
        // create the main panel
        JPanel panel = new JPanel(new BorderLayout());

        // create the search area
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField("Smith", 25);
        JButton searchButton = new JButton("Search Author");

        topPanel.add(new JLabel("Author name:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        JTable table = new JTable();

        searchButton.addActionListener(e -> {
            try {
                // use LIKE so partial author names can match
                String pattern = "%" + searchField.getText().trim() + "%";

                // load authors and their conference/journal article counts
                DefaultTableModel rawModel = runPreparedQuery("""
                        SELECT
                            a.author_id,
                            a.author_name,
                            (SELECT COUNT(*)
                             FROM Conference_Article_Authors caa
                             WHERE caa.author_id = a.author_id) AS conference_articles,
                            (SELECT COUNT(*)
                             FROM Journal_Article_Authors jaa
                             WHERE jaa.author_id = a.author_id) AS journal_articles
                        FROM Authors a
                        WHERE a.author_name LIKE ?
                        ORDER BY
                            ((SELECT COUNT(*)
                              FROM Conference_Article_Authors caa
                              WHERE caa.author_id = a.author_id)
                            +
                             (SELECT COUNT(*)
                              FROM Journal_Article_Authors jaa
                              WHERE jaa.author_id = a.author_id)) DESC
                        LIMIT 30
                        """, pattern);

                DefaultTableModel model = new DefaultTableModel(
                        new String[]{"author_id", "author_name", "conference_articles", "journal_articles", "total_articles"},
                        0
                );

                // add a total_articles column in Java
                for (int i = 0; i < rawModel.getRowCount(); i++) {
                    int conf = Integer.parseInt(rawModel.getValueAt(i, 2).toString());
                    int journal = Integer.parseInt(rawModel.getValueAt(i, 3).toString());

                    model.addRow(new Object[]{
                            rawModel.getValueAt(i, 0),
                            rawModel.getValueAt(i, 1),
                            conf,
                            journal,
                            conf + journal
                    });
                }

                // show the final model in the table
                table.setModel(model);

            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // run the default search when the tab opens
        searchButton.doClick();

        return panel;
    }

    // this creates the year profile tab
    private JPanel createYearProfilePanel() {
    // create the main panel
    JPanel panel = new JPanel(new BorderLayout());

    // create the year input area
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JTextField yearField = new JTextField("2012", 10);
    JButton searchButton = new JButton("Load Year Profile");

    topPanel.add(new JLabel("Year:"));
    topPanel.add(yearField);
    topPanel.add(searchButton);

    JTable table = new JTable();
    SimpleBarChart chart = new SimpleBarChart("Year profile summary");
    SimpleScatterChart scatterChart = new SimpleScatterChart("Year profile summary - Scatter Plot");

    // put the charts next to each other
    JPanel chartsPanel = createChartRow(chart, scatterChart);

    // put the table above the chart
    JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table),
            chartsPanel
    );
    splitPane.setResizeWeight(0.55);

    searchButton.addActionListener(e -> {
        try {
            // read the year from the text field
            String year = yearField.getText().trim();

            // load one summary row for each year metric
            DefaultTableModel model = runPreparedQuery("""
                    SELECT 'Conference articles' AS metric, COUNT(*) AS value
                    FROM Conference_Articles
                    WHERE year = ?

                    UNION ALL

                    SELECT 'Journal articles', COUNT(*)
                    FROM Journal_Articles
                    WHERE year = ?

                    UNION ALL

                    SELECT 'Distinct conferences', COUNT(DISTINCT conf_id)
                    FROM Conference_Articles
                    WHERE year = ?

                    UNION ALL

                    SELECT 'Distinct journals', COUNT(DISTINCT journal_id)
                    FROM Journal_Articles
                    WHERE year = ?

                    UNION ALL

                    SELECT 'Distinct authors', COUNT(DISTINCT author_id)
                    FROM (
                        SELECT caa.author_id
                        FROM Conference_Article_Authors caa
                        JOIN Conference_Articles ca ON caa.article_id = ca.article_id
                        WHERE ca.year = ?

                        UNION

                        SELECT jaa.author_id
                        FROM Journal_Article_Authors jaa
                        JOIN Journal_Articles ja ON jaa.article_id = ja.article_id
                        WHERE ja.year = ?
                    ) x
                    """, year, year, year, year, year, year);

            // show the data in the table and chart
            table.setModel(model);
            chart.updateFromTable(model, 0, 1);
            scatterChart.updateFromTable(model, 0, 1);

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(splitPane, BorderLayout.CENTER);

    // run the default search when the tab opens
    searchButton.doClick();

    return panel;
}

// this creates the publication details tab
private JPanel createPublicationDetailsPanel() {
    // create the main panel
    JPanel panel = new JPanel(new BorderLayout());

    // create the search area
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JTextField yearField = new JTextField("2012", 8);
    JTextField filterField = new JTextField("", 25);
    JButton searchButton = new JButton("Search Publications");

    topPanel.add(new JLabel("Year:"));
    topPanel.add(yearField);
    topPanel.add(new JLabel("Filter title / venue:"));
    topPanel.add(filterField);
    topPanel.add(searchButton);

    JTable table = new JTable();

    searchButton.addActionListener(e -> {
        try {
            // read the search values
            String year = yearField.getText().trim();
            String pattern = "%" + filterField.getText().trim() + "%";

            // load conference and journal publications together
            DefaultTableModel model = runPreparedQuery("""
                    SELECT
                        source_type,
                        article_id,
                        title,
                        year,
                        venue,
                        pages
                    FROM (
                        SELECT
                            'Conference' AS source_type,
                            ca.article_id,
                            ca.title,
                            ca.year,
                            CONCAT(c.acronym, ' - ', c.title) AS venue,
                            ca.pages
                        FROM Conference_Articles ca
                        JOIN Conferences c ON ca.conf_id = c.conf_id
                        WHERE ca.year = ?
                          AND (
                                c.acronym LIKE ?
                                OR c.title LIKE ?
                                OR ca.title LIKE ?
                              )

                        UNION ALL

                        SELECT
                            'Journal' AS source_type,
                            ja.article_id,
                            ja.title,
                            ja.year,
                            j.title AS venue,
                            ja.pages
                        FROM Journal_Articles ja
                        JOIN Journals j ON ja.journal_id = j.journal_id
                        WHERE ja.year = ?
                          AND (
                                j.title LIKE ?
                                OR ja.title LIKE ?
                              )
                    ) x
                    ORDER BY source_type, venue, title
                    LIMIT 500
                    """, year, pattern, pattern, pattern, year, pattern, pattern);

            // show the publications in the table
            table.setModel(model);

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);

    // run the default search when the tab opens
    searchButton.doClick();

    return panel;
}


// this creates the venue profile tab
private JPanel createVenueProfilePanel() {
    // create the main panel
    JPanel panel = new JPanel(new BorderLayout());

    // create the venue search controls
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JComboBox<String> typeBox = new JComboBox<>(new String[]{"Conference", "Journal"});
    JTextField searchField = new JTextField("EDBT", 25);
    JButton searchButton = new JButton("Load Venue Profile");

    topPanel.add(new JLabel("Venue type:"));
    topPanel.add(typeBox);
    topPanel.add(new JLabel("Search:"));
    topPanel.add(searchField);
    topPanel.add(searchButton);

    JTable profileTable = new JTable();
    JTable yearlyTable = new JTable();

    // create charts for yearly venue activity
    SimpleBarChart barChart = new SimpleBarChart("Venue articles per year - Bar Chart");
    SimpleLineChart lineChart = new SimpleLineChart("Venue articles per year - Line Chart");
    SimpleScatterChart scatterChart = new SimpleScatterChart("Venue articles per year - Scatter Plot");

    // put the charts next to each other
    JPanel chartsPanel = createChartRow(barChart, lineChart, scatterChart);

    // put the yearly table above the charts
    JSplitPane bottomSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(yearlyTable),
            chartsPanel
    );
    bottomSplit.setResizeWeight(0.50);

    // put the profile table above the yearly section
    JSplitPane mainSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(profileTable),
            bottomSplit
    );
    mainSplit.setResizeWeight(0.35);

    searchButton.addActionListener(e -> {
        try {
            // read the venue type and search text
            String type = typeBox.getSelectedItem().toString();
            String pattern = "%" + searchField.getText().trim() + "%";

            // run conference queries when conference is selected
            if (type.equals("Conference")) {
                // load one profile row per matching conference
                DefaultTableModel profileModel = runPreparedQuery("""
                        SELECT
                            c.conf_id,
                            c.acronym,
                            c.title,
                            c.rank_category,
                            c.primary_for,
                            MIN(ca.year) AS first_year,
                            MAX(ca.year) AS last_year,
                            COUNT(DISTINCT ca.article_id) AS total_articles,
                            COUNT(caa.author_id) AS total_author_occurrences,
                            COUNT(DISTINCT caa.author_id) AS distinct_authors,
                            ROUND(COUNT(caa.author_id) / NULLIF(COUNT(DISTINCT ca.article_id), 0), 2) AS avg_authors_per_article
                        FROM Conferences c
                        LEFT JOIN Conference_Articles ca
                            ON ca.conf_id = c.conf_id
                        LEFT JOIN Conference_Article_Authors caa
                            ON caa.article_id = ca.article_id
                        WHERE c.acronym LIKE ?
                           OR c.title LIKE ?
                        GROUP BY
                            c.conf_id,
                            c.acronym,
                            c.title,
                            c.rank_category,
                            c.primary_for
                        ORDER BY total_articles DESC
                        LIMIT 20
                        """, pattern, pattern);

                profileTable.setModel(profileModel);

                // load yearly article counts for the matching conference
                DefaultTableModel yearlyModel = runPreparedQuery("""
                        SELECT
                            ca.year,
                            COUNT(DISTINCT ca.article_id) AS article_count,
                            COUNT(caa.author_id) AS total_author_occurrences,
                            COUNT(DISTINCT caa.author_id) AS distinct_authors
                        FROM Conferences c
                        JOIN Conference_Articles ca
                            ON ca.conf_id = c.conf_id
                        LEFT JOIN Conference_Article_Authors caa
                            ON caa.article_id = ca.article_id
                        WHERE c.acronym LIKE ?
                           OR c.title LIKE ?
                        GROUP BY ca.year
                        ORDER BY ca.year
                        """, pattern, pattern);

                // show the yearly data in the table and charts
                yearlyTable.setModel(yearlyModel);
                barChart.updateFromTable(yearlyModel, 0, 1);
                lineChart.updateFromTable(yearlyModel, 0, 1);
                scatterChart.updateFromTable(yearlyModel, 0, 1);

            } else {
                // load one profile row per matching journal
                DefaultTableModel profileModel = runPreparedQuery("""
                        SELECT
                            j.journal_id,
                            j.title,
                            j.country,
                            j.sjr_index,
                            j.best_quartile,
                            j.total_docs_3y,
                            j.total_refs,
                            j.cites_per_doc_2y,
                            MIN(ja.year) AS first_year,
                            MAX(ja.year) AS last_year,
                            COUNT(DISTINCT ja.article_id) AS total_articles,
                            COUNT(jaa.author_id) AS total_author_occurrences,
                            COUNT(DISTINCT jaa.author_id) AS distinct_authors,
                            ROUND(COUNT(jaa.author_id) / NULLIF(COUNT(DISTINCT ja.article_id), 0), 2) AS avg_authors_per_article
                        FROM Journals j
                        LEFT JOIN Journal_Articles ja
                            ON ja.journal_id = j.journal_id
                        LEFT JOIN Journal_Article_Authors jaa
                            ON jaa.article_id = ja.article_id
                        WHERE j.title LIKE ?
                        GROUP BY
                            j.journal_id,
                            j.title,
                            j.country,
                            j.sjr_index,
                            j.best_quartile,
                            j.total_docs_3y,
                            j.total_refs,
                            j.cites_per_doc_2y
                        ORDER BY total_articles DESC
                        LIMIT 20
                        """, pattern);

                profileTable.setModel(profileModel);

                // load yearly article counts for the matching journal
                DefaultTableModel yearlyModel = runPreparedQuery("""
                        SELECT
                            ja.year,
                            COUNT(DISTINCT ja.article_id) AS article_count,
                            COUNT(jaa.author_id) AS total_author_occurrences,
                            COUNT(DISTINCT jaa.author_id) AS distinct_authors
                        FROM Journals j
                        JOIN Journal_Articles ja
                            ON ja.journal_id = j.journal_id
                        LEFT JOIN Journal_Article_Authors jaa
                            ON jaa.article_id = ja.article_id
                        WHERE j.title LIKE ?
                        GROUP BY ja.year
                        ORDER BY ja.year
                        """, pattern);

                // show the yearly data in the table and charts
                yearlyTable.setModel(yearlyModel);
                barChart.updateFromTable(yearlyModel, 0, 1);
                lineChart.updateFromTable(yearlyModel, 0, 1);
                scatterChart.updateFromTable(yearlyModel, 0, 1);
            }

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    typeBox.addActionListener(e -> {
        // change the example search text when the venue type changes
        if (typeBox.getSelectedItem().toString().equals("Conference")) {
            searchField.setText("EDBT");
        } else {
            searchField.setText("Information Systems");
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(mainSplit, BorderLayout.CENTER);

    // run the default search when the tab opens
    searchButton.doClick();

    return panel;
}


// this creates the author profile tab
private JPanel createAuthorProfilePanel() {
    // create the main panel
    JPanel panel = new JPanel(new BorderLayout());

    // create the author search area
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JTextField searchField = new JTextField("John R. Smith", 25);
    JButton searchButton = new JButton("Load Author Profile");

    topPanel.add(new JLabel("Author name:"));
    topPanel.add(searchField);
    topPanel.add(searchButton);

    JTable profileTable = new JTable();
    JTable yearlyTable = new JTable();

    // create charts for yearly author activity
    SimpleBarChart barChart = new SimpleBarChart("Author articles per year - Bar Chart");
    SimpleLineChart lineChart = new SimpleLineChart("Author articles per year - Line Chart");
    SimpleScatterChart scatterChart = new SimpleScatterChart("Author articles per year - Scatter Plot");

    // put the charts next to each other
    JPanel chartsPanel = createChartRow(barChart, lineChart, scatterChart);

    // put the yearly table above the charts
    JSplitPane bottomSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(yearlyTable),
            chartsPanel
    );
    bottomSplit.setResizeWeight(0.50);

    // put the author profile above the yearly section
    JSplitPane mainSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(profileTable),
            bottomSplit
    );
    mainSplit.setResizeWeight(0.35);

    searchButton.addActionListener(e -> {
        try {
            // use LIKE so partial author names can match
            String pattern = "%" + searchField.getText().trim() + "%";

            // load the author profile summary
            DefaultTableModel profileModel = runPreparedQuery("""
                    SELECT
                        a.author_id,
                        a.author_name,
                        MIN(x.year) AS first_year,
                        MAX(x.year) AS last_year,
                        SUM(CASE WHEN x.source_type = 'Conference' THEN 1 ELSE 0 END) AS conference_articles,
                        SUM(CASE WHEN x.source_type = 'Journal' THEN 1 ELSE 0 END) AS journal_articles,
                        COUNT(*) AS total_articles,
                        ROUND(COUNT(*) / NULLIF((MAX(x.year) - MIN(x.year) + 1), 0), 2) AS avg_articles_per_year
                    FROM Authors a
                    JOIN (
                        SELECT
                            caa.author_id,
                            ca.year,
                            'Conference' AS source_type
                        FROM Conference_Article_Authors caa
                        JOIN Conference_Articles ca
                            ON caa.article_id = ca.article_id

                        UNION ALL

                        SELECT
                            jaa.author_id,
                            ja.year,
                            'Journal' AS source_type
                        FROM Journal_Article_Authors jaa
                        JOIN Journal_Articles ja
                            ON jaa.article_id = ja.article_id
                    ) x ON x.author_id = a.author_id
                    WHERE a.author_name LIKE ?
                    GROUP BY a.author_id, a.author_name
                    ORDER BY total_articles DESC
                    LIMIT 10
                    """, pattern);

            profileTable.setModel(profileModel);

            // clear charts and show a message if no author matched
            if (profileModel.getRowCount() == 0) {
                yearlyTable.setModel(new DefaultTableModel());
                DefaultTableModel emptyModel = new DefaultTableModel(
                        new String[]{"year", "total_articles"}, 0
                );
                barChart.updateFromTable(emptyModel, 0, 1);
                lineChart.updateFromTable(emptyModel, 0, 1);
                scatterChart.updateFromTable(emptyModel, 0, 1);
                JOptionPane.showMessageDialog(
                        panel,
                        "No author found for: " + searchField.getText(),
                        "No Results",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            // use the first matching author for the yearly profile
            String authorId = profileModel.getValueAt(0, 0).toString();

            // load yearly article counts for that author
            DefaultTableModel yearlyModel = runPreparedQuery("""
                    SELECT
                        year,
                        SUM(conference_articles) AS conference_articles,
                        SUM(journal_articles) AS journal_articles,
                        SUM(conference_articles + journal_articles) AS total_articles
                    FROM (
                        SELECT
                            ca.year,
                            COUNT(*) AS conference_articles,
                            0 AS journal_articles
                        FROM Conference_Article_Authors caa
                        JOIN Conference_Articles ca
                            ON caa.article_id = ca.article_id
                        WHERE caa.author_id = ?
                        GROUP BY ca.year

                        UNION ALL

                        SELECT
                            ja.year,
                            0 AS conference_articles,
                            COUNT(*) AS journal_articles
                        FROM Journal_Article_Authors jaa
                        JOIN Journal_Articles ja
                            ON jaa.article_id = ja.article_id
                        WHERE jaa.author_id = ?
                        GROUP BY ja.year
                    ) yearly
                    GROUP BY year
                    ORDER BY year
                    """, authorId, authorId);

            // show the yearly data in the table and charts
            yearlyTable.setModel(yearlyModel);
            barChart.updateFromTable(yearlyModel, 0, 3);
            lineChart.updateFromTable(yearlyModel, 0, 3);
            scatterChart.updateFromTable(yearlyModel, 0, 3);

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(mainSplit, BorderLayout.CENTER);

    // run the default search when the tab opens
    searchButton.doClick();

    return panel;
}


// this creates the top analytics tab
private JPanel createTopAnalyticsPanel() {
    // create the main panel
    JPanel panel = new JPanel(new BorderLayout());

    // create the report selector
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JComboBox<String> analyticsBox = new JComboBox<>(new String[]{
            "Top 20 Conferences by Articles",
            "Top 20 Journals by Articles",
            "Top 20 Authors by Articles",
            "Top Years by Publication Count"
    });

    JButton loadButton = new JButton("Load Analytics");

    topPanel.add(new JLabel("Analytics report:"));
    topPanel.add(analyticsBox);
    topPanel.add(loadButton);

    JTable table = new JTable();
    SimpleBarChart chart = new SimpleBarChart("Top Analytics");
    SimpleScatterChart scatterChart = new SimpleScatterChart("Top Analytics - Scatter Plot");

    // put the charts next to each other
    JPanel chartsPanel = createChartRow(chart, scatterChart);

    // put the table above the chart
    JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table),
            chartsPanel
    );
    splitPane.setResizeWeight(0.55);

    loadButton.addActionListener(e -> {
        try {
            // read the selected report name
            String selected = analyticsBox.getSelectedItem().toString();

            // load top conferences
            if (selected.equals("Top 20 Conferences by Articles")) {
                DefaultTableModel model = runQuery("""
                        SELECT
                            c.conf_id,
                            c.acronym,
                            c.title,
                            COUNT(ca.article_id) AS total_articles,
                            MIN(ca.year) AS first_year,
                            MAX(ca.year) AS last_year,
                            COUNT(DISTINCT caa.author_id) AS distinct_authors,
                            ROUND(COUNT(caa.author_id) / NULLIF(COUNT(DISTINCT ca.article_id), 0), 2) AS avg_authors_per_article
                        FROM Conferences c
                        JOIN Conference_Articles ca
                            ON ca.conf_id = c.conf_id
                        LEFT JOIN Conference_Article_Authors caa
                            ON caa.article_id = ca.article_id
                        GROUP BY
                            c.conf_id,
                            c.acronym,
                            c.title
                        ORDER BY total_articles DESC
                        LIMIT 20
                        """);

                // show the report in the table and chart
                table.setModel(model);
                chart.title = "Top 20 conferences by number of articles";
                scatterChart.title = "Top 20 conferences by number of articles";
                chart.updateFromTable(model, 1, 3);
                scatterChart.updateFromTable(model, 1, 3);

            // load top journals
            } else if (selected.equals("Top 20 Journals by Articles")) {
                DefaultTableModel model = runQuery("""
                        SELECT
                            j.journal_id,
                            j.title,
                            j.country,
                            j.best_quartile,
                            COUNT(ja.article_id) AS total_articles,
                            MIN(ja.year) AS first_year,
                            MAX(ja.year) AS last_year,
                            COUNT(DISTINCT jaa.author_id) AS distinct_authors,
                            ROUND(COUNT(jaa.author_id) / NULLIF(COUNT(DISTINCT ja.article_id), 0), 2) AS avg_authors_per_article
                        FROM Journals j
                        JOIN Journal_Articles ja
                            ON ja.journal_id = j.journal_id
                        LEFT JOIN Journal_Article_Authors jaa
                            ON jaa.article_id = ja.article_id
                        GROUP BY
                            j.journal_id,
                            j.title,
                            j.country,
                            j.best_quartile
                        ORDER BY total_articles DESC
                        LIMIT 20
                        """);

                // show the report in the table and chart
                table.setModel(model);
                chart.title = "Top 20 journals by number of articles";
                scatterChart.title = "Top 20 journals by number of articles";
                chart.updateFromTable(model, 1, 4);
                scatterChart.updateFromTable(model, 1, 4);

            // load top authors
            } else if (selected.equals("Top 20 Authors by Articles")) {
                DefaultTableModel model = runQuery("""
                        SELECT
                            a.author_id,
                            a.author_name,
                            SUM(x.total_articles) AS total_articles,
                            SUM(x.conference_articles) AS conference_articles,
                            SUM(x.journal_articles) AS journal_articles
                        FROM Authors a
                        JOIN (
                            SELECT
                                author_id,
                                COUNT(*) AS total_articles,
                                COUNT(*) AS conference_articles,
                                0 AS journal_articles
                            FROM Conference_Article_Authors
                            GROUP BY author_id

                            UNION ALL

                            SELECT
                                author_id,
                                COUNT(*) AS total_articles,
                                0 AS conference_articles,
                                COUNT(*) AS journal_articles
                            FROM Journal_Article_Authors
                            GROUP BY author_id
                        ) x ON x.author_id = a.author_id
                        GROUP BY
                            a.author_id,
                            a.author_name
                        ORDER BY total_articles DESC
                        LIMIT 20
                        """);

                // show the report in the table and chart
                table.setModel(model);
                chart.title = "Top 20 authors by number of articles";
                scatterChart.title = "Top 20 authors by number of articles";
                chart.updateFromTable(model, 1, 2);
                scatterChart.updateFromTable(model, 1, 2);

            // load top years
            } else if (selected.equals("Top Years by Publication Count")) {
                DefaultTableModel model = runQuery("""
                        SELECT
                            year,
                            SUM(conference_articles) AS conference_articles,
                            SUM(journal_articles) AS journal_articles,
                            SUM(conference_articles + journal_articles) AS total_publications
                        FROM (
                            SELECT
                                year,
                                COUNT(*) AS conference_articles,
                                0 AS journal_articles
                            FROM Conference_Articles
                            GROUP BY year

                            UNION ALL

                            SELECT
                                year,
                                0 AS conference_articles,
                                COUNT(*) AS journal_articles
                            FROM Journal_Articles
                            GROUP BY year
                        ) yearly
                        GROUP BY year
                        ORDER BY total_publications DESC
                        LIMIT 20
                        """);

                // show the report in the table and chart
                table.setModel(model);
                chart.title = "Top years by total publication count";
                scatterChart.title = "Top years by total publication count";
                chart.updateFromTable(model, 0, 3);
                scatterChart.updateFromTable(model, 0, 3);
            }

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(splitPane, BorderLayout.CENTER);

    // load the default report when the tab opens
    loadButton.doClick();

    return panel;
}

    // this sets the general Swing style
    private static void applyPolishedLookAndFeel() {
        // use the operating system style when possible
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep the default Swing style if this fails
        }

        // set common Swing fonts before components are created
        UIManager.put("Button.font", BOLD_FONT);
        UIManager.put("ComboBox.font", BASE_FONT);
        UIManager.put("Label.font", BASE_FONT);
        UIManager.put("Table.font", BASE_FONT);
        UIManager.put("TableHeader.font", BOLD_FONT);
        UIManager.put("TabbedPane.font", BOLD_FONT);
        UIManager.put("TextField.font", BASE_FONT);
    }

    // this applies styling to a component and its children
    private static void polishComponentTree(Component component) {
        // polish this component first
        polishComponent(component);

        // then polish its child components
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                polishComponentTree(child);
            }
        }
    }

    // this chooses the right styling method for one component
    private static void polishComponent(Component component) {
        // give every component the same base font
        component.setFont(BASE_FONT);

        if (component instanceof SimpleBarChart || component instanceof SimpleLineChart || component instanceof SimpleScatterChart) {
            return;
        }

        if (component instanceof JPanel) {
            polishPanel((JPanel) component);
        }
        if (component instanceof JButton) {
            polishButton((JButton) component);
        }
        if (component instanceof JTable) {
            polishTable((JTable) component);
        }
        if (component instanceof JScrollPane) {
            polishScrollPane((JScrollPane) component);
        }
        if (component instanceof JTabbedPane) {
            polishTabs((JTabbedPane) component);
        }
        if (component instanceof JSplitPane) {
            polishSplitPane((JSplitPane) component);
        }
        if (component instanceof JTextField) {
            polishTextField((JTextField) component);
        }
        if (component instanceof JComboBox) {
            polishComboBox((JComboBox<?>) component);
        }
        if (component instanceof JLabel) {
            polishLabel((JLabel) component);
        }
    }

    // this styles a panel
    private static void polishPanel(JPanel panel) {
        // toolbars get a white background and padding
        if (panel.getLayout() instanceof FlowLayout) {
            panel.setBackground(SURFACE_COLOR);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
            return;
        }

        // main panels get a soft page background
        panel.setBackground(PAGE_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    // this styles a button
    private static void polishButton(JButton button) {
        // make action buttons look consistent
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(BOLD_FONT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
    }

    // this styles a table
    private static void polishTable(JTable table) {
        // make tables easier to scan
        table.setRowHeight(28);
        table.setFont(BASE_FONT);
        table.setSelectionBackground(SELECTION_COLOR);
        table.setSelectionForeground(TEXT_COLOR);
        table.setGridColor(BORDER_COLOR);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        // style the table header
        table.getTableHeader().setFont(BOLD_FONT);
        table.getTableHeader().setBackground(new Color(235, 240, 248));
        table.getTableHeader().setForeground(TEXT_COLOR);
        table.getTableHeader().setReorderingAllowed(false);
    }

    // this styles a scroll pane
    private static void polishScrollPane(JScrollPane scrollPane) {
        // give scroll areas a clean border
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getViewport().setBackground(SURFACE_COLOR);
    }

    // this styles the tabbed pane
    private static void polishTabs(JTabbedPane tabs) {
        // add spacing around the tabbed dashboard
        tabs.setBackground(PAGE_BACKGROUND);
        tabs.setForeground(TEXT_COLOR);
        tabs.setFont(BOLD_FONT);
        tabs.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    // this styles a split pane
    private static void polishSplitPane(JSplitPane splitPane) {
        // make split panes feel less heavy
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(8);
        splitPane.setContinuousLayout(true);
        splitPane.setBackground(PAGE_BACKGROUND);
    }

    // this styles a text field
    private static void polishTextField(JTextField textField) {
        // make text fields match the dashboard spacing
        textField.setForeground(TEXT_COLOR);
        textField.setBackground(SURFACE_COLOR);
        textField.setCaretColor(PRIMARY_COLOR);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
    }

    // this styles a dropdown
    private static void polishComboBox(JComboBox<?> comboBox) {
        // make dropdowns match text fields
        comboBox.setForeground(TEXT_COLOR);
        comboBox.setBackground(SURFACE_COLOR);
        comboBox.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
    }

    // this styles a label
    private static void polishLabel(JLabel label) {
        // make labels slightly softer than table text
        label.setForeground(MUTED_TEXT_COLOR);
        label.setFont(BASE_FONT);
    }

    // this creates a row of charts
    private static JPanel createChartRow(JComponent... charts) {
        // place charts in one clean horizontal row
        JPanel chartPanel = new JPanel(new GridLayout(1, charts.length, 10, 0));
        chartPanel.setBackground(PAGE_BACKGROUND);
        chartPanel.setBorder(BorderFactory.createEmptyBorder());

        // add each chart to the row
        for (JComponent chart : charts) {
            chartPanel.add(chart);
        }

        return chartPanel;
    }

    // this runs a normal SQL query
    private DefaultTableModel runQuery(String sql) throws SQLException {
        // run a SQL query without parameters
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            // turn the database result into a table model
            return resultSetToTableModel(resultSet);
        }
    }

    // this runs a SQL query with parameters
    private DefaultTableModel runPreparedQuery(String sql, String... parameters) throws SQLException {
        // prepare a SQL query that uses ? placeholders
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // put each parameter into the prepared statement
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }

            // run the prepared query
            try (ResultSet resultSet = statement.executeQuery()) {
                // turn the database result into a table model
                return resultSetToTableModel(resultSet);
            }
        }
    }

    // this converts SQL results into a table model
    private DefaultTableModel resultSetToTableModel(ResultSet resultSet) throws SQLException {
        // get information about the result columns
        ResultSetMetaData metaData = resultSet.getMetaData();

        int columnCount = metaData.getColumnCount();
        String[] columnNames = new String[columnCount];

        // copy the database column names
        for (int i = 1; i <= columnCount; i++) {
            columnNames[i - 1] = metaData.getColumnLabel(i);
        }

        DefaultTableModel model = new DefaultTableModel(columnNames, 0);

        // copy every database row into the table model
        while (resultSet.next()) {
            Object[] row = new Object[columnCount];

            for (int i = 1; i <= columnCount; i++) {
                row[i - 1] = resultSet.getObject(i);
            }

            model.addRow(row);
        }

        return model;
    }

    // this shows database errors to the user
    private void showError(Exception ex) {
        // print the error for debugging
        ex.printStackTrace();

        // show the error to the user
        JOptionPane.showMessageDialog(
                null,
                ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    // this class draws a simple bar chart
    static class SimpleBarChart extends JPanel {
        String title;
        private List<String> labels = new ArrayList<>();
        private List<Integer> values = new ArrayList<>();

        // this creates a bar chart
        public SimpleBarChart(String title) {
            // save chart title and basic style
            this.title = title;
            setPreferredSize(new Dimension(900, 250));
            setBackground(SURFACE_COLOR);
            setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        }

        // this loads table data into the bar chart
        public void updateFromTable(DefaultTableModel model, int labelColumn, int valueColumn) {
            // clear old chart data
            labels.clear();
            values.clear();

            // read labels and numbers from the table model
            for (int i = 0; i < model.getRowCount(); i++) {
                Object label = model.getValueAt(i, labelColumn);
                Object value = model.getValueAt(i, valueColumn);

                if (label != null && value != null) {
                    labels.add(label.toString());
                    values.add(Integer.parseInt(value.toString()));
                }
            }

            // redraw the chart
            repaint();
        }

        @Override
        // this draws the bar chart
        protected void paintComponent(Graphics graphics) {
            // clear the chart before drawing again
            super.paintComponent(graphics);

            // use Graphics2D for smoother drawing
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // get the current panel size
            int width = getWidth();
            int height = getHeight();

            // draw the chart title
            g.setFont(TITLE_FONT);
            g.setColor(TEXT_COLOR);
            g.drawString(title, 20, 25);

            // show a message when there is no data
            if (values.isEmpty()) {
                g.setFont(BASE_FONT);
                g.setColor(MUTED_TEXT_COLOR);
                g.drawString("No data to display.", 20, 55);
                return;
            }

            // find the largest value for scaling
            int max = 1;
            for (int value : values) {
                if (value > max) {
                    max = value;
                }
            }

            // set chart drawing area
            int left = 50;
            int bottom = height - 45;
            int chartWidth = width - 80;
            int chartHeight = height - 90;

            // calculate bar width
            int barCount = values.size();
            int barWidth = Math.max(3, chartWidth / Math.max(1, barCount));

            // draw chart axes
            g.setColor(CHART_GRID_COLOR);
            g.drawLine(left, bottom, left + chartWidth, bottom);
            g.drawLine(left, bottom, left, bottom - chartHeight);

            // draw each bar
            for (int i = 0; i < barCount; i++) {
                int value = values.get(i);
                int barHeight = (int) ((value / (double) max) * chartHeight);

                int x = left + i * barWidth;
                int y = bottom - barHeight;

                g.setColor(CHART_ACCENT_COLOR);
                g.fillRect(x, y, Math.max(2, barWidth - 2), barHeight);

                // draw labels only when there are not too many
                if (barCount <= 35) {
                    g.setFont(BASE_FONT);
                    g.setColor(MUTED_TEXT_COLOR);
                    g.drawString(labels.get(i), x, bottom + 15);
                }
            }

            g.setFont(BASE_FONT);
            g.setColor(MUTED_TEXT_COLOR);
            g.drawString("Max: " + max, left + 5, bottom - chartHeight - 10);
        }
    }

    // this class draws a simple line chart
    static class SimpleLineChart extends JPanel {
        public String title;
        private List<String> labels = new ArrayList<>();
        private List<Integer> values = new ArrayList<>();

        // this creates a line chart
        public SimpleLineChart(String title) {
            // save chart title and basic style
            this.title = title;
            setPreferredSize(new Dimension(900, 250));
            setBackground(SURFACE_COLOR);
            setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        }

        // this loads table data into the line chart
        public void updateFromTable(DefaultTableModel model, int labelColumn, int valueColumn) {
            // clear old chart data
            labels.clear();
            values.clear();

            // read labels and numbers from the table model
            for (int i = 0; i < model.getRowCount(); i++) {
                Object label = model.getValueAt(i, labelColumn);
                Object value = model.getValueAt(i, valueColumn);

                if (label != null && value != null) {
                    labels.add(label.toString());
                    values.add(Integer.parseInt(value.toString()));
                }
            }

            // redraw the chart
            repaint();
        }

        @Override
        // this draws the line chart
        protected void paintComponent(Graphics graphics) {
            // clear the chart before drawing again
            super.paintComponent(graphics);

            // use Graphics2D for smoother drawing
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // get the current panel size
            int width = getWidth();
            int height = getHeight();

            // draw the chart title
            g.setFont(TITLE_FONT);
            g.setColor(TEXT_COLOR);
            g.drawString(title, 20, 25);

            // show a message when there is no data
            if (values.isEmpty()) {
                g.setFont(BASE_FONT);
                g.setColor(MUTED_TEXT_COLOR);
                g.drawString("No data to display.", 20, 55);
                return;
            }

            // find the largest value for scaling
            int max = 1;
            for (int value : values) {
                if (value > max) {
                    max = value;
                }
            }

            // set chart drawing area
            int left = 55;
            int right = width - 35;
            int top = 50;
            int bottom = height - 45;

            int chartWidth = right - left;
            int chartHeight = bottom - top;

            // draw chart axes
            g.setFont(BASE_FONT);
            g.setColor(CHART_GRID_COLOR);
            g.drawLine(left, bottom, right, bottom);
            g.drawLine(left, bottom, left, top);
            g.setColor(MUTED_TEXT_COLOR);
            g.drawString("Max: " + max, left + 5, top - 10);

            int n = values.size();

            // draw one point in the middle if there is only one value
            if (n == 1) {
                int x = left + chartWidth / 2;
                int y = bottom - (int) ((values.get(0) / (double) max) * chartHeight);

                g.setColor(CHART_ACCENT_COLOR);
                g.fillOval(x - 4, y - 4, 8, 8);
                g.setColor(MUTED_TEXT_COLOR);
                g.drawString(labels.get(0), x - 10, bottom + 15);
                return;
            }

            int previousX = -1;
            int previousY = -1;
            g.setStroke(new BasicStroke(2f));

            // draw each point and connect it to the previous point
            for (int i = 0; i < n; i++) {
                int value = values.get(i);

                int x = left + (int) ((i / (double) (n - 1)) * chartWidth);
                int y = bottom - (int) ((value / (double) max) * chartHeight);

                if (previousX != -1) {
                    g.setColor(CHART_ACCENT_COLOR);
                    g.drawLine(previousX, previousY, x, y);
                }

                g.setColor(CHART_ACCENT_COLOR);
                g.fillOval(x - 3, y - 3, 6, 6);

                previousX = x;
                previousY = y;

                // draw fewer labels when there are many points
                if (n <= 20 || i % Math.max(1, n / 10) == 0) {
                    g.setColor(MUTED_TEXT_COLOR);
                    g.drawString(labels.get(i), x - 10, bottom + 15);
                }
            }
        }
    }

    // this class draws a simple scatter plot
    static class SimpleScatterChart extends JPanel {
        public String title;
        private List<String> labels = new ArrayList<>();
        private List<Integer> values = new ArrayList<>();

        // this creates a scatter plot
        public SimpleScatterChart(String title) {
            // save chart title and basic style
            this.title = title;
            setPreferredSize(new Dimension(900, 250));
            setBackground(SURFACE_COLOR);
            setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        }

        // this loads table data into the scatter plot
        public void updateFromTable(DefaultTableModel model, int labelColumn, int valueColumn) {
            // clear old chart data
            labels.clear();
            values.clear();

            // read labels and numbers from the table model
            for (int i = 0; i < model.getRowCount(); i++) {
                Object label = model.getValueAt(i, labelColumn);
                Object value = model.getValueAt(i, valueColumn);

                if (label != null && value != null) {
                    labels.add(label.toString());
                    values.add(Integer.parseInt(value.toString()));
                }
            }

            // redraw the chart
            repaint();
        }

        @Override
        // this draws the scatter plot
        protected void paintComponent(Graphics graphics) {
            // clear the chart before drawing again
            super.paintComponent(graphics);

            // use Graphics2D for smoother drawing
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // get the current panel size
            int width = getWidth();
            int height = getHeight();

            // draw the chart title
            g.setFont(TITLE_FONT);
            g.setColor(TEXT_COLOR);
            g.drawString(title, 20, 25);

            // show a message when there is no data
            if (values.isEmpty()) {
                g.setFont(BASE_FONT);
                g.setColor(MUTED_TEXT_COLOR);
                g.drawString("No data to display.", 20, 55);
                return;
            }

            // find the largest value for scaling
            int max = 1;
            for (int value : values) {
                if (value > max) {
                    max = value;
                }
            }

            // set chart drawing area
            int left = 55;
            int right = width - 35;
            int top = 50;
            int bottom = height - 45;

            int chartWidth = right - left;
            int chartHeight = bottom - top;

            // draw chart axes
            g.setFont(BASE_FONT);
            g.setColor(CHART_GRID_COLOR);
            g.drawLine(left, bottom, right, bottom);
            g.drawLine(left, bottom, left, top);

            g.setColor(MUTED_TEXT_COLOR);
            g.drawString("Max: " + max, left + 5, top - 10);

            int pointCount = values.size();

            // draw one point in the middle if there is only one value
            if (pointCount == 1) {
                int x = left + chartWidth / 2;
                int y = bottom - (int) ((values.get(0) / (double) max) * chartHeight);

                g.setColor(CHART_ACCENT_COLOR);
                g.fillOval(x - 5, y - 5, 10, 10);
                g.setColor(MUTED_TEXT_COLOR);
                g.drawString(labels.get(0), x - 10, bottom + 15);
                return;
            }

            // draw each point without connecting lines
            for (int i = 0; i < pointCount; i++) {
                int value = values.get(i);

                int x = left + (int) ((i / (double) (pointCount - 1)) * chartWidth);
                int y = bottom - (int) ((value / (double) max) * chartHeight);

                g.setColor(CHART_ACCENT_COLOR);
                g.fillOval(x - 5, y - 5, 10, 10);

                g.setColor(SURFACE_COLOR);
                g.drawOval(x - 5, y - 5, 10, 10);

                // draw fewer labels when there are many points
                if (pointCount <= 20 || i % Math.max(1, pointCount / 10) == 0) {
                    g.setColor(MUTED_TEXT_COLOR);
                    g.drawString(labels.get(i), x - 10, bottom + 15);
                }
            }
        }
    }

}
