package au.org.intersect.samifier.runner;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.List;

import au.org.intersect.samifier.domain.CodonTranslationTable;
import au.org.intersect.samifier.domain.DebuggingFlag;
import au.org.intersect.samifier.domain.Genome;
import au.org.intersect.samifier.domain.PeptideSearchResult;
import au.org.intersect.samifier.domain.PeptideSequence;
import au.org.intersect.samifier.domain.ProteinToOLNMap;
import au.org.intersect.samifier.domain.ResultsAnalyserOutputter;
import au.org.intersect.samifier.generator.PeptideSequenceGenerator;
import au.org.intersect.samifier.generator.PeptideSequenceGeneratorImpl;
import au.org.intersect.samifier.parser.GenomeParserImpl;
import au.org.intersect.samifier.parser.PeptideSearchResultsParser;
import au.org.intersect.samifier.parser.PeptideSearchResultsParserImpl;
import au.org.intersect.samifier.parser.ProteinToOLNParser;
import au.org.intersect.samifier.parser.ProteinToOLNParserImpl;
import au.org.intersect.samifier.reporter.DatabaseHelper;

public class ResultAnalyserRunner {
    private File searchResultsFile;
    private File genomeFile;
    private File proteinToOLNMapFile;
    private File outputFile;
    private File chromosomeDir;
    private File translationTableFile;

    private static DatabaseHelper hsqldb;

    public ResultAnalyserRunner(File searchResultsFile, File genomeFile,
            File proteinToOLNMapFile, File outputFile, File chromosomeDir,
            File translationTableFile) throws Exception {
        this.searchResultsFile = searchResultsFile;
        this.genomeFile = genomeFile;
        this.proteinToOLNMapFile = proteinToOLNMapFile;
        this.outputFile = outputFile;
        this.chromosomeDir = chromosomeDir;
        this.translationTableFile = translationTableFile;
    }

    public ResultAnalyserRunner(File searchResultsFile, File genomeFile,
            File proteinToOLNMapFile, File outputFile, File chromosomeDir)
            throws Exception {
        this.searchResultsFile = searchResultsFile;
        this.genomeFile = genomeFile;
        this.proteinToOLNMapFile = proteinToOLNMapFile;
        this.outputFile = outputFile;
        this.chromosomeDir = chromosomeDir;
    }

    public void initMemoryDb() throws Exception {
        hsqldb = new DatabaseHelper();
        hsqldb.connect();
        hsqldb.generateTables();
    }

    public void run() throws Exception {
        GenomeParserImpl genomeParser = new GenomeParserImpl();
        Genome genome = genomeParser.parseGenomeFile(genomeFile);

        ProteinToOLNParser proteinToOLNParser = new ProteinToOLNParserImpl();
        ProteinToOLNMap proteinToOLNMap = proteinToOLNParser
                .parseMappingFile(proteinToOLNMapFile);

        PeptideSearchResultsParser peptideSearchResultsParser = new PeptideSearchResultsParserImpl(
                proteinToOLNMap);
        List<PeptideSearchResult> peptideSearchResults = peptideSearchResultsParser
                .parseResults(searchResultsFile);
        peptideSearchResults = peptideSearchResultsParser
                .sortResultsByChromosome(peptideSearchResults, proteinToOLNMap,
                        genome);
        PeptideSequenceGenerator sequenceGenerator = new PeptideSequenceGeneratorImpl(
                genome, proteinToOLNMap, chromosomeDir);

        FileWriter output = new FileWriter(outputFile);

        if (DebuggingFlag.get_sbi_debug_flag() == 1) {
            CodonTranslationTable translationTable = CodonTranslationTable
                    .parseTableFile(translationTableFile);

            for (PeptideSearchResult peptideSearchResult : peptideSearchResults) {
                PeptideSequence peptideSequence = sequenceGenerator
                        .getPeptideSequence(peptideSearchResult);
                if (peptideSequence == null) {
                    continue;
                }
                ResultsAnalyserOutputter outputter;
                outputter = new ResultsAnalyserOutputter(peptideSearchResult,
                        proteinToOLNMap, genome, peptideSequence,
                        translationTable);
                output.write(outputter.toString());
                output.write(System.getProperty("line.separator"));
            }

        } else {
            for (PeptideSearchResult peptideSearchResult : peptideSearchResults) {
                PeptideSequence peptideSequence = sequenceGenerator
                        .getPeptideSequence(peptideSearchResult);
                if (peptideSequence == null) {
                    continue;
                }
                ResultsAnalyserOutputter outputter;
                outputter = new ResultsAnalyserOutputter(peptideSearchResult,
                        proteinToOLNMap, genome, peptideSequence);
                output.write(outputter.toString());
                output.write(System.getProperty("line.separator"));
            }

        }

        output.close();

    }

    public void runWithQuery(String sqlQuery) throws Exception {
        GenomeParserImpl genomeParser = new GenomeParserImpl();
        Genome genome = genomeParser.parseGenomeFile(genomeFile);

        ProteinToOLNParser proteinToOLNParser = new ProteinToOLNParserImpl();
        ProteinToOLNMap proteinToOLNMap = proteinToOLNParser
                .parseMappingFile(proteinToOLNMapFile);

        PeptideSearchResultsParser peptideSearchResultsParser = new PeptideSearchResultsParserImpl(
                proteinToOLNMap);
        List<PeptideSearchResult> peptideSearchResults = peptideSearchResultsParser
                .parseResults(searchResultsFile);
        PeptideSequenceGenerator sequenceGenerator = new PeptideSequenceGeneratorImpl(
                genome, proteinToOLNMap, chromosomeDir);
        peptideSearchResults = peptideSearchResultsParser
                .sortResultsByChromosome(peptideSearchResults, proteinToOLNMap,
                        genome);
        FileWriter output = new FileWriter(outputFile);

        for (PeptideSearchResult peptideSearchResult : peptideSearchResults) {
            PeptideSequence peptideSequence = sequenceGenerator
                    .getPeptideSequence(peptideSearchResult);
            if (peptideSequence == null || peptideSequence.getNucleotideSequence().isEmpty()) {
                continue;
            }

            if (DebuggingFlag.get_sbi_debug_flag() == 1) {
                CodonTranslationTable translationTable = CodonTranslationTable
                        .parseTableFile(translationTableFile);
                ResultsAnalyserOutputter outputter = new ResultsAnalyserOutputter(
                        peptideSearchResult, proteinToOLNMap, genome,
                        peptideSequence, translationTable);
                String query = outputter.toQuery();
                // System.out.println(query);
                hsqldb.execute(query);
            } else {
                ResultsAnalyserOutputter outputter = new ResultsAnalyserOutputter(
                        peptideSearchResult, proteinToOLNMap, genome,
                        peptideSequence);
                hsqldb.execute(outputter.toQuery());
            }
        }

        Collection<String> resultSet = hsqldb.filterResult(sqlQuery);
        for (String set : resultSet) {
            output.write(set);
            output.write(System.getProperty("line.separator"));
        }

        output.close();
        hsqldb.shutdown();
    }
}
