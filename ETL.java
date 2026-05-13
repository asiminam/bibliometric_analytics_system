import java.io.*;
import java.text.Normalizer;
import java.util.*;

public class ETL {

    public static void main(String[] args) {
        // Map to hold unique authors. 
        // Key -> author_name, Value -> author_id
        Map<String, Integer> authorsMap = new HashMap<>();

        // Sets to prevent duplicate Article-Author relationships
        Set<String> seenConfRelations = new HashSet<>();
        Set<String> seenJournalRelations = new HashSet<>();

        // Input files
        String confInputFile = "input_inproceedings.csv"; 
        String journalInputFile = "input_article.csv"; 

        // Output files
        String cleanAuthorsFile = "Authors_Clean.csv";
        String cleanConfAuthorsFile = "Conference_Article_Authors_Clean.csv";
        String cleanJournalAuthorsFile = "Journal_Article_Authors_Clean.csv";

        try (
            PrintWriter authorsWriter = new PrintWriter(new FileWriter(cleanAuthorsFile));
            PrintWriter confRelationsWriter = new PrintWriter(new FileWriter(cleanConfAuthorsFile));
            PrintWriter journalRelationsWriter = new PrintWriter(new FileWriter(cleanJournalAuthorsFile))
        ) {
            

            // Process Conferences
            System.out.println("Starting ETL for Conferences (inproceedings)...");
            processFile(confInputFile, authorsMap, seenConfRelations, authorsWriter, confRelationsWriter);

            // Process Journals
            System.out.println("\nStarting ETL for Journals (articles)...");
            processFile(journalInputFile, authorsMap, seenJournalRelations, authorsWriter, journalRelationsWriter);

            System.out.println("\nETL process completed successfully for both files!");
            System.out.println("Total unique authors across all publications: " + authorsMap.size());

        } catch (IOException e) {
            System.err.println("Fatal Error writing output files.");
            e.printStackTrace();
        }
    }


    private static void processFile(String inputFile, Map<String, Integer> authorsMap, 
                                    Set<String> seenRelations, PrintWriter authorsWriter, 
                                    PrintWriter relationsWriter) {
        
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            reader.readLine(); // Skip header
            int linesProcessed = 0;

            while ((line = reader.readLine()) != null) {
                linesProcessed++;
                String[] columns = line.split(";", -1); 

                if (columns.length < 2 || columns[1] == null || columns[1].trim().isEmpty() || columns[1].equalsIgnoreCase("NULL")) {
                    continue; 
                }

                String articleId = columns[0].trim(); 
                String authorsString = columns[1].trim();
                String[] individualAuthors = authorsString.split("\\|");

                for (String authorName : individualAuthors) {
                    authorName = authorName.trim();
                    if (authorName.isEmpty()) continue;

                    // used normalizeName to be able to compare authors
                    String normalizedAuthorName = normalizeName(authorName);

                    // If author doesn't exist add them
                    if (!authorsMap.containsKey(normalizedAuthorName)) {
                        int newAuthorId = authorsMap.size() + 1; // Map size works as auto-increment ID
                        authorsMap.put(normalizedAuthorName, newAuthorId);
                        
                        // Write the original name as it was
                        authorsWriter.println(newAuthorId + "\t" + authorName); 
                    }
                    
                    int currentAuthorId = authorsMap.get(normalizedAuthorName);
                    String relationKey = articleId + "-" + currentAuthorId;
                    
                    // Remove duplicates from Article-Author relationships
                    if (!seenRelations.contains(relationKey)) {
                        relationsWriter.println(articleId + "\t" + currentAuthorId);
                        seenRelations.add(relationKey);
                    }
                }
            }
            System.out.println("Finished processing. Processed " + linesProcessed + " lines.");
        } catch (FileNotFoundException e) {
            System.err.println("File " + inputFile + " not found. Skipping.");
        } catch (IOException e) {
            System.err.println("Error reading file " + inputFile);
            e.printStackTrace();
        }
    }

    // We need to normalize the author names to process them properly in SQL and not have any duplicates
   private static String normalizeName(String name) {
        // Turns every letter to lowercase
        String normalized = name.toLowerCase();
        
        // Replace special characters with ones SQL recognizes
        normalized = normalized.replace("ß", "ss");
        normalized = normalized.replace("æ", "ae");
        normalized = normalized.replace("œ", "oe");
        
        // Removes accents
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        
        // Removes double spaces 
        normalized = normalized.replaceAll("\\s+", " ");
     
        return normalized.trim();
    }
}