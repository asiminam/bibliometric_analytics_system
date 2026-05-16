# Bibliometric Analytics System

A Java and MySQL desktop application for cleaning, integrating, querying, and visualizing bibliometric publication data.

The system processes raw conference and journal publication datasets, transforms them into clean relational tables, loads them into a MySQL database, and provides an interactive Java Swing dashboard for exploring publication trends, author activity, venue profiles, and top analytics.

## Project Overview

This project was developed for a database systems course assignment focused on:

- Data integration from different bibliographic sources
- ETL processing and data cleaning
- Relational database design
- SQL-based analytics
- Interactive data visualization
- Java desktop application development

The main goal is to help a user analyze research publication data and discover patterns such as publication trends over time, active authors, top conferences, top journals, and yearly research activity.

## Technologies Used

- Java
- Java Swing
- MySQL
- JDBC
- SQL
- CSV / TSV data processing

## Main Features

### ETL Process

The `ETL.java` file reads raw bibliographic data and creates clean files that can be loaded into MySQL.

The ETL process includes:

- Reading conference article data
- Reading journal article data
- Cleaning text values
- Handling missing or invalid rows
- Creating unique IDs for authors, conferences, journals, and articles
- Removing duplicate article-author relationships
- Generating clean output files for database loading
- Supporting optional ranking data for conferences and journals

Generated processed files include:

```text
data/processed/Authors_Clean.csv
data/processed/Conferences_Clean.csv
data/processed/Journals_Clean.csv
data/processed/Conference_Articles_Clean.csv
data/processed/Journal_Articles_Clean.csv
data/processed/Conference_Article_Authors_Clean.csv
data/processed/Journal_Article_Authors_Clean.csv