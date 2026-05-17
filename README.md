# Bibliometric Analytics System

A Java and MySQL desktop application for cleaning, integrating, querying, and visualizing bibliometric publication data.

The system processes raw conference and journal publication datasets, transforms them into clean relational tables, loads them into a MySQL database, and provides an interactive Java Swing dashboard for exploring publication trends, author activity, venue profiles, yearly analytics, and top rankings.

## Project Overview

This project was developed for a database systems course assignment focused on data integration and data visualization.

The purpose of the system is to support an analyst who wants to explore bibliometric data through a clean relational database and an interactive dashboard. The application combines an ETL process, a MySQL back-end, SQL-based business logic, and a Java Swing graphical interface.

The main goals of the project are:

- To clean and integrate bibliographic data from different sources
- To create a relational database that can be queried efficiently
- To provide ready-made analytical reports
- To visualize publication trends and patterns
- To allow users to explore conferences, journals, authors, years, and publications interactively

## Technologies Used

- Java
- Java Swing
- MySQL
- JDBC
- SQL
- CSV / TSV file processing

## Main Components

### ETL Process

The ETL process is implemented in `ETL.java`.

This part of the system reads the raw input files, cleans the data, creates IDs, removes duplicates, and writes the final processed files that can be loaded into MySQL.

The ETL process handles:

- Conference article data
- Journal article data
- Author extraction
- Conference extraction
- Journal extraction
- Article-author relationships
- Optional conference ranking data
- Optional journal ranking data
- Duplicate article detection
- Duplicate article-author relation detection
- Missing or invalid row filtering
- Text cleaning and normalization

The default raw input files are:

```text
 data/raw/input_inproceedings.csv
 data/raw/input_article.csv
```

The processed output files are:

```text
 data/processed/Authors_Clean.csv
 data/processed/Conferences_Clean.csv
 data/processed/Journals_Clean.csv
 data/processed/Conference_Articles_Clean.csv
 data/processed/Journal_Articles_Clean.csv
 data/processed/Conference_Article_Authors_Clean.csv
 data/processed/Journal_Article_Authors_Clean.csv
```

### Database

The project uses a MySQL database named:

```text
bookdata_4991
```

The database stores the cleaned bibliometric data in relational tables.

Main tables include:

- `Authors`
- `Conferences`
- `Journals`
- `Conference_Articles`
- `Journal_Articles`
- `Conference_Article_Authors`
- `Journal_Article_Authors`

The database works as the back-end of the dashboard. The dashboard uses SQL queries through JDBC to calculate statistics and display results.

### Dashboard Application

The dashboard is implemented in `Dashboard.java`.

It is a Java Swing desktop application that connects to the MySQL database and provides interactive tabs for exploring the data.

When the application starts, it asks the user to enter the MySQL root password. Then it opens the main dashboard window.

The dashboard includes the following tabs:

#### Database Summary

Shows the number of rows in the main database tables.

#### Conference Yearly Stats

Allows the user to search for a conference by acronym or title and view yearly article counts.

Includes:

- Results table
- Bar chart
- Line chart
- Scatter plot

#### Journal Yearly Stats

Allows the user to search for a journal by title and view yearly article counts.

Includes:

- Results table
- Bar chart
- Line chart
- Scatter plot

#### Author Search

Allows the user to search for authors by name.

Shows:

- Author ID
- Author name
- Number of conference articles
- Number of journal articles
- Total number of articles

#### Year Profile

Allows the user to enter a year and view a summary of publication activity for that year.

Shows metrics such as:

- Conference articles
- Journal articles
- Distinct conferences
- Distinct journals
- Distinct authors

#### Publication Details

Allows the user to search publications by year and filter by title or venue.

Shows both conference and journal publications in one table.

#### Venue Profile

Allows the user to view a detailed profile for a conference or journal.

For conferences, it can show:

- Conference ID
- Acronym
- Title
- Ranking category
- Primary field of research
- First publication year
- Last publication year
- Total articles
- Total author occurrences
- Distinct authors
- Average authors per article
- Yearly activity

For journals, it can show:

- Journal ID
- Title
- Country
- SJR index
- Best quartile
- Total documents
- Total references
- Cites per document
- First publication year
- Last publication year
- Total articles
- Distinct authors
- Average authors per article
- Yearly activity

#### Author Profile

Allows the user to search for an author and view publication statistics.

Shows author activity over time using tables and charts.

#### Top Analytics

Shows top-ranked results based on publication counts.

Examples include:

- Top conferences by number of articles
- Top journals by number of articles
- Top authors by number of articles
- Top years by publication activity

## Visualizations

The dashboard supports several types of visualizations:

- Bar charts
- Line charts
- Scatter plots
- SQL result tables

These visualizations help the user identify trends, compare publication activity, and understand patterns in the data.

Examples of supported analysis include:

- Articles per conference per year
- Articles per journal per year
- Articles per author per year
- Publication activity in a selected year
- Venue activity over time
- Top authors, conferences, journals, and years

## Project Structure

```text
Bibliometric_Analytics_System/
│
├── src/
│   ├── ETL.java
│   └── Dashboard.java
│
├── data/
│   ├── raw/
│   │   ├── input_inproceedings.csv
│   │   ├── input_article.csv
│   │   ├── conference_ranking.csv
│   │   └── journal_ranking_data_raw.csv
│   │
│   ├── processed/
│   │   ├── Authors_Clean.csv
│   │   ├── Conferences_Clean.csv
│   │   ├── Journals_Clean.csv
│   │   ├── Conference_Articles_Clean.csv
│   │   ├── Journal_Articles_Clean.csv
│   │   ├── Conference_Article_Authors_Clean.csv
│   │   └── Journal_Article_Authors_Clean.csv
│   │
│   └── rejected/
│       └── Rejected_Rows.csv
│
├── sql/
│   ├── schema.sql
│   └── load_data.sql
│
├── lib/
│   └── mysql-connector-j.jar
│
└── README.md

## Requirements

Before running the project, make sure you have installed:

- Java JDK
- MySQL Server
- MySQL Connector/J

You also need the raw data files placed in the correct folder.

## How to Run the Project

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/bibliometric-analytics-system.git
cd bibliometric-analytics-system
```

Replace `your-username` with your GitHub username.

### 2. Add the Raw Data Files

Place the raw data files inside:

```text
data/raw/
```

Required files:

```text
input_inproceedings.csv
input_article.csv
```

Optional ranking files:

```text
conference_ranking.csv
journal_ranking_data_raw.csv
```

### 3. Add MySQL Connector/J

Place the MySQL Connector/J `.jar` file inside:

```text
lib/
```

Example:

```text
lib/mysql-connector-j.jar
```

If your connector file has a longer name, use that exact file name in the commands below.

### 4. Compile the Java Files

For Windows:

```bash
javac -cp ".;lib/mysql-connector-j.jar" src\ETL.java src\Dashboard.java
```

For macOS / Linux:

```bash
javac -cp ".:lib/mysql-connector-j.jar" src/ETL.java src/Dashboard.java
```

### 5. Run the ETL Process

For Windows:

```bash
java -cp ".;lib/mysql-connector-j.jar" src.ETL
```

For macOS / Linux:

```bash
java -cp ".:lib/mysql-connector-j.jar" src.ETL
```

After the ETL process finishes, the clean files will be created inside:

```text
data/processed/
```

### 6. Create and Load the MySQL Database

Open MySQL and run your SQL scripts.

Example:

```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p bookdata_4991 < sql/load_data.sql
```

If your SQL files have different names, replace the file names in the commands.

### 7. Run the Dashboard

For Windows:

```bash
java -cp ".;lib/mysql-connector-j.jar" src.Dashboard
```

For macOS / Linux:

```bash
java -cp ".:lib/mysql-connector-j.jar" src.Dashboard
```

The application will ask for the MySQL root password and then open the dashboard.

## Example Workflow

A typical workflow is:

1. Place the raw data files in `data/raw/`
2. Run `ETL.java`
3. Load the processed files into MySQL
4. Run `Dashboard.java`
5. Explore the data through the dashboard tabs

## Example Use Cases

The system can be used to answer questions such as:

- How many articles were published in a specific year?
- Which conferences have the most publications?
- Which journals have the most publications?
- How has a conference's activity changed over time?
- How has a journal's activity changed over time?
- Which authors have the most publications?
- What is the publication profile of a specific author?
- What publications exist for a selected year?
- What is the profile of a selected conference or journal?

## Notes

- The dashboard connects to the database `bookdata_4991`.
- MySQL must be running before the dashboard starts.
- The MySQL Connector/J file must be included in the classpath.
- The raw data files may not be included in the repository if they are too large or provided separately by the course.
- The `data/processed/` files are generated by the ETL process.
- The application currently uses the MySQL `root` user and asks for the password at startup.

## Future Improvements

Possible future improvements include:

- Add year-range filters to more dashboard tabs
- Add export options for tables and reports
- Add more advanced author collaboration analytics
- Add better handling for missing database tables
- Add configuration settings for database username and password
- Add more advanced venue comparison tools
- Add saved reports for common analytics queries

## Author

Developed by Asimina Mamasoula.
