package src;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Dashboard {

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/bookdata_4991?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";
    private static final String DB_USER = "root";

    private Connection connection;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Dashboard().start();
            } catch (Exception e) {
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

    private void start() throws Exception {
        String password = JOptionPane.showInputDialog(
                null,
                "Enter MySQL root password:",
                "Database Login",
                JOptionPane.QUESTION_MESSAGE
        );

        if (password == null) {
            return;
        }

        connection = DriverManager.getConnection(DB_URL, DB_USER, password);

        JFrame frame = new JFrame("Bibliometric Analytics System - Phase 2");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Database Summary", createSummaryPanel());
        tabs.addTab("Conference Yearly Stats", createConferencePanel());
        tabs.addTab("Journal Yearly Stats", createJournalPanel());
        tabs.addTab("Author Search", createAuthorPanel());
        tabs.addTab("Year Profile", createYearProfilePanel());
        tabs.addTab("Publication Details", createPublicationDetailsPanel());
        tabs.addTab("Venue Profile", createVenueProfilePanel());
        tabs.addTab("Author Profile", createAuthorProfilePanel());
        tabs.addTab("Top Analytics", createTopAnalyticsPanel());

        frame.add(tabs);
        frame.setVisible(true);
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JTable table = new JTable();
        JButton loadButton = new JButton("Load Summary");

        loadButton.addActionListener(e -> {
            try {
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

        loadButton.doClick();

        return panel;
    }

    private JPanel createConferencePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField("EDBT", 25);
        JButton searchButton = new JButton("Search Conference");

        topPanel.add(new JLabel("Conference acronym/title:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        JTable table = new JTable();

        SimpleBarChart barChart = new SimpleBarChart("Conference articles per year - Bar Chart");
        SimpleLineChart lineChart = new SimpleLineChart("Conference articles per year - Line Chart");

        JSplitPane chartsPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                barChart,
                lineChart
        );
        chartsPane.setResizeWeight(0.50);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table),
                chartsPane
        );
        splitPane.setResizeWeight(0.55);

        searchButton.addActionListener(e -> {
            try {
                String pattern = "%" + searchField.getText().trim() + "%";

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

                table.setModel(model);
                barChart.updateFromTable(model, 3, 4);
                lineChart.updateFromTable(model, 3, 4);

            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);

        searchButton.doClick();

        return panel;
    }

    private JPanel createJournalPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField("Information Systems", 25);
        JButton searchButton = new JButton("Search Journal");

        topPanel.add(new JLabel("Journal title:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        JTable table = new JTable();

        SimpleBarChart barChart = new SimpleBarChart("Journal articles per year - Bar Chart");
        SimpleLineChart lineChart = new SimpleLineChart("Journal articles per year - Line Chart");

        JSplitPane chartsPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                barChart,
                lineChart
        );
        chartsPane.setResizeWeight(0.50);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table),
                chartsPane
        );
        splitPane.setResizeWeight(0.55);

        searchButton.addActionListener(e -> {
            try {
                String pattern = "%" + searchField.getText().trim() + "%";

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

                table.setModel(model);
                barChart.updateFromTable(model, 2, 3);
                lineChart.updateFromTable(model, 2, 3);

            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);

        searchButton.doClick();

        return panel;
    }

    private JPanel createAuthorPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField("Smith", 25);
        JButton searchButton = new JButton("Search Author");

        topPanel.add(new JLabel("Author name:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        JTable table = new JTable();

        searchButton.addActionListener(e -> {
            try {
                String pattern = "%" + searchField.getText().trim() + "%";

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

                table.setModel(model);

            } catch (SQLException ex) {
                showError(ex);
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        searchButton.doClick();

        return panel;
    }

    private JPanel createYearProfilePanel() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JTextField yearField = new JTextField("2012", 10);
    JButton searchButton = new JButton("Load Year Profile");

    topPanel.add(new JLabel("Year:"));
    topPanel.add(yearField);
    topPanel.add(searchButton);

    JTable table = new JTable();
    SimpleBarChart chart = new SimpleBarChart("Year profile summary");

    JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table),
            chart
    );
    splitPane.setResizeWeight(0.55);

    searchButton.addActionListener(e -> {
        try {
            String year = yearField.getText().trim();

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

            table.setModel(model);
            chart.updateFromTable(model, 0, 1);

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(splitPane, BorderLayout.CENTER);

    searchButton.doClick();

    return panel;
}

private JPanel createPublicationDetailsPanel() {
    JPanel panel = new JPanel(new BorderLayout());

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
            String year = yearField.getText().trim();
            String pattern = "%" + filterField.getText().trim() + "%";

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

            table.setModel(model);

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);

    searchButton.doClick();

    return panel;
}


private JPanel createVenueProfilePanel() {
    JPanel panel = new JPanel(new BorderLayout());

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

    SimpleBarChart barChart = new SimpleBarChart("Venue articles per year - Bar Chart");
    SimpleLineChart lineChart = new SimpleLineChart("Venue articles per year - Line Chart");

    JSplitPane chartsPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            barChart,
            lineChart
    );
    chartsPane.setResizeWeight(0.50);

    JSplitPane bottomSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(yearlyTable),
            chartsPane
    );
    bottomSplit.setResizeWeight(0.50);

    JSplitPane mainSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(profileTable),
            bottomSplit
    );
    mainSplit.setResizeWeight(0.35);

    searchButton.addActionListener(e -> {
        try {
            String type = typeBox.getSelectedItem().toString();
            String pattern = "%" + searchField.getText().trim() + "%";

            if (type.equals("Conference")) {
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

                yearlyTable.setModel(yearlyModel);
                barChart.updateFromTable(yearlyModel, 0, 1);
                lineChart.updateFromTable(yearlyModel, 0, 1);

            } else {
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

                yearlyTable.setModel(yearlyModel);
                barChart.updateFromTable(yearlyModel, 0, 1);
                lineChart.updateFromTable(yearlyModel, 0, 1);
            }

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    typeBox.addActionListener(e -> {
        if (typeBox.getSelectedItem().toString().equals("Conference")) {
            searchField.setText("EDBT");
        } else {
            searchField.setText("Information Systems");
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(mainSplit, BorderLayout.CENTER);

    searchButton.doClick();

    return panel;
}


private JPanel createAuthorProfilePanel() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JTextField searchField = new JTextField("John R. Smith", 25);
    JButton searchButton = new JButton("Load Author Profile");

    topPanel.add(new JLabel("Author name:"));
    topPanel.add(searchField);
    topPanel.add(searchButton);

    JTable profileTable = new JTable();
    JTable yearlyTable = new JTable();

    SimpleBarChart barChart = new SimpleBarChart("Author articles per year - Bar Chart");
    SimpleLineChart lineChart = new SimpleLineChart("Author articles per year - Line Chart");

    JSplitPane chartsPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            barChart,
            lineChart
    );
    chartsPane.setResizeWeight(0.50);

    JSplitPane bottomSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(yearlyTable),
            chartsPane
    );
    bottomSplit.setResizeWeight(0.50);

    JSplitPane mainSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(profileTable),
            bottomSplit
    );
    mainSplit.setResizeWeight(0.35);

    searchButton.addActionListener(e -> {
        try {
            String pattern = "%" + searchField.getText().trim() + "%";

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

            if (profileModel.getRowCount() == 0) {
                yearlyTable.setModel(new DefaultTableModel());
                DefaultTableModel emptyModel = new DefaultTableModel(
                        new String[]{"year", "total_articles"}, 0
                );
                barChart.updateFromTable(emptyModel, 0, 1);
                lineChart.updateFromTable(emptyModel, 0, 1);
                JOptionPane.showMessageDialog(
                        panel,
                        "No author found for: " + searchField.getText(),
                        "No Results",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            String authorId = profileModel.getValueAt(0, 0).toString();

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

            yearlyTable.setModel(yearlyModel);
            barChart.updateFromTable(yearlyModel, 0, 3);
            lineChart.updateFromTable(yearlyModel, 0, 3);

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(mainSplit, BorderLayout.CENTER);

    searchButton.doClick();

    return panel;
}


private JPanel createTopAnalyticsPanel() {
    JPanel panel = new JPanel(new BorderLayout());

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

    JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table),
            chart
    );
    splitPane.setResizeWeight(0.55);

    loadButton.addActionListener(e -> {
        try {
            String selected = analyticsBox.getSelectedItem().toString();

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

                table.setModel(model);
                chart.title = "Top 20 conferences by number of articles";
                chart.updateFromTable(model, 1, 3);

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

                table.setModel(model);
                chart.title = "Top 20 journals by number of articles";
                chart.updateFromTable(model, 1, 4);

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

                table.setModel(model);
                chart.title = "Top 20 authors by number of articles";
                chart.updateFromTable(model, 1, 2);

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

                table.setModel(model);
                chart.title = "Top years by total publication count";
                chart.updateFromTable(model, 0, 3);
            }

        } catch (SQLException ex) {
            showError(ex);
        }
    });

    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(splitPane, BorderLayout.CENTER);

    loadButton.doClick();

    return panel;
}

    private DefaultTableModel runQuery(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSetToTableModel(resultSet);
        }
    }

    private DefaultTableModel runPreparedQuery(String sql, String... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSetToTableModel(resultSet);
            }
        }
    }

    private DefaultTableModel resultSetToTableModel(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();

        int columnCount = metaData.getColumnCount();
        String[] columnNames = new String[columnCount];

        for (int i = 1; i <= columnCount; i++) {
            columnNames[i - 1] = metaData.getColumnLabel(i);
        }

        DefaultTableModel model = new DefaultTableModel(columnNames, 0);

        while (resultSet.next()) {
            Object[] row = new Object[columnCount];

            for (int i = 1; i <= columnCount; i++) {
                row[i - 1] = resultSet.getObject(i);
            }

            model.addRow(row);
        }

        return model;
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(
                null,
                ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    static class SimpleBarChart extends JPanel {
        String title;
        private List<String> labels = new ArrayList<>();
        private List<Integer> values = new ArrayList<>();

        public SimpleBarChart(String title) {
            this.title = title;
            setPreferredSize(new Dimension(900, 250));
            setBackground(Color.WHITE);
        }

        public void updateFromTable(DefaultTableModel model, int labelColumn, int valueColumn) {
            labels.clear();
            values.clear();

            for (int i = 0; i < model.getRowCount(); i++) {
                Object label = model.getValueAt(i, labelColumn);
                Object value = model.getValueAt(i, valueColumn);

                if (label != null && value != null) {
                    labels.add(label.toString());
                    values.add(Integer.parseInt(value.toString()));
                }
            }

            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g.setColor(Color.BLACK);
            g.drawString(title, 20, 25);

            if (values.isEmpty()) {
                g.drawString("No data to display.", 20, 55);
                return;
            }

            int max = 1;
            for (int value : values) {
                if (value > max) {
                    max = value;
                }
            }

            int left = 50;
            int bottom = height - 45;
            int chartWidth = width - 80;
            int chartHeight = height - 90;

            int barCount = values.size();
            int barWidth = Math.max(3, chartWidth / Math.max(1, barCount));

            g.drawLine(left, bottom, left + chartWidth, bottom);
            g.drawLine(left, bottom, left, bottom - chartHeight);

            for (int i = 0; i < barCount; i++) {
                int value = values.get(i);
                int barHeight = (int) ((value / (double) max) * chartHeight);

                int x = left + i * barWidth;
                int y = bottom - barHeight;

                g.fillRect(x, y, Math.max(2, barWidth - 2), barHeight);

                if (barCount <= 35) {
                    g.drawString(labels.get(i), x, bottom + 15);
                }
            }

            g.drawString("Max: " + max, left + 5, bottom - chartHeight - 10);
        }
    }

    static class SimpleLineChart extends JPanel {
        public String title;
        private List<String> labels = new ArrayList<>();
        private List<Integer> values = new ArrayList<>();

        public SimpleLineChart(String title) {
            this.title = title;
            setPreferredSize(new Dimension(900, 250));
            setBackground(Color.WHITE);
        }

        public void updateFromTable(DefaultTableModel model, int labelColumn, int valueColumn) {
            labels.clear();
            values.clear();

            for (int i = 0; i < model.getRowCount(); i++) {
                Object label = model.getValueAt(i, labelColumn);
                Object value = model.getValueAt(i, valueColumn);

                if (label != null && value != null) {
                    labels.add(label.toString());
                    values.add(Integer.parseInt(value.toString()));
                }
            }

            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g.setColor(Color.BLACK);
            g.drawString(title, 20, 25);

            if (values.isEmpty()) {
                g.drawString("No data to display.", 20, 55);
                return;
            }

            int max = 1;
            for (int value : values) {
                if (value > max) {
                    max = value;
                }
            }

            int left = 55;
            int right = width - 35;
            int top = 50;
            int bottom = height - 45;

            int chartWidth = right - left;
            int chartHeight = bottom - top;

            g.drawLine(left, bottom, right, bottom);
            g.drawLine(left, bottom, left, top);
            g.drawString("Max: " + max, left + 5, top - 10);

            int n = values.size();

            if (n == 1) {
                int x = left + chartWidth / 2;
                int y = bottom - (int) ((values.get(0) / (double) max) * chartHeight);

                g.fillOval(x - 4, y - 4, 8, 8);
                g.drawString(labels.get(0), x - 10, bottom + 15);
                return;
            }

            int previousX = -1;
            int previousY = -1;

            for (int i = 0; i < n; i++) {
                int value = values.get(i);

                int x = left + (int) ((i / (double) (n - 1)) * chartWidth);
                int y = bottom - (int) ((value / (double) max) * chartHeight);

                if (previousX != -1) {
                    g.drawLine(previousX, previousY, x, y);
                }

                g.fillOval(x - 3, y - 3, 6, 6);

                previousX = x;
                previousY = y;

                if (n <= 20 || i % Math.max(1, n / 10) == 0) {
                    g.drawString(labels.get(i), x - 10, bottom + 15);
                }
            }
        }
    }

}