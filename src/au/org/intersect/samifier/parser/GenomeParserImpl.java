package au.org.intersect.samifier.parser;

import au.org.intersect.samifier.domain.GeneInfo;
import au.org.intersect.samifier.domain.GeneSequence;
import au.org.intersect.samifier.domain.Genome;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenomeParserImpl implements GenomeParser {
    private static Logger LOG = Logger.getLogger(GenomeParserImpl.class);
    public static final Pattern GENE_RE = Pattern.compile("^(gene|gene_cassette|pseudogene|transposable_element_gene)$");
    private static final String STRAND_FORWARD = "+";
    private static final Pattern STRAND_RE = Pattern.compile("^([" + STRAND_FORWARD + "]|[-])$");
    public static final String CODING_SEQUENCE = "CDS";
    public static final String INTRON = "intron";
    public static final Pattern SEQUENCE_RE = Pattern.compile("(" + CODING_SEQUENCE + "|" + INTRON + ")");
    private static final Pattern ID_ATTRIBUTE_RE = Pattern.compile(".*Name=([^_;]+).*");
    private static final Pattern PARENT_ATTRIBUTE_RE = Pattern.compile(".*Parent=([^_;]+).*");

    private String genomeFileName;
    private int lineNumber = 0;
    private String line;

    public GenomeParserImpl() {

    }

    public Genome parseGenomeFile(File genomeFile)
            throws GenomeFileParsingException {
        try {
            genomeFileName = genomeFile.getAbsolutePath();
            return doParsing(genomeFile);
        } catch (IOException e) {
            throw new GenomeFileParsingException(e.getMessage());
        }

    }

    private Genome doParsing(File genomeFile) throws IOException,
            GenomeFileParsingException {
        Genome genome = new Genome();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(genomeFile));

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.matches("^\\s*#")) {
                    continue;
                }
                // chromosome, source, type, start, stop, score, strand, phase,
                // attributes
                String[] parts = line.split("\\t", 9);
                if (parts.length < 9) {
                    // throw new
                    // GenomeFileParsingException("Line "+lineNumber+": not in expected format");
                    LOG.warn("Line " + lineNumber + ": not in expected format");
                    continue;
                }
                String type = parts[2];
                if (type == null) {
                    continue;
                }
                if (GENE_RE.matcher(type).matches()) {
                    GeneInfo gene = parseGene(parts);
                    processGene(genome, gene);
                } else if (SEQUENCE_RE.matcher(type).matches()) {
                    GeneSequence sequence = parseSequence(parts);
                    processSequence(genome, parts[CHROMOSOME_PART], sequence);
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return genome;
    }

    private void throwParsingException(String message)
            throws GenomeFileParsingException {
        throw new GenomeFileParsingException("Error in " + genomeFileName + ":"
                + lineNumber + " " + line + "\n > " + message);
    }

    private int parseStrand(String direction) throws GenomeFileParsingException {
        if (!STRAND_RE.matcher(direction).matches()) {
            throwParsingException("Invalid strand " + direction);
        }
        return STRAND_FORWARD.equals(direction) ? 1 : -1;
    }

    private boolean parseSequenceType(String type)
            throws GenomeFileParsingException {
        return CODING_SEQUENCE.equals(type);
    }

    protected GeneInfo parseGene(String[] parts)
            throws GenomeFileParsingException {
        String chromosome = parts[CHROMOSOME_PART];
        int start = Integer.parseInt(parts[START_PART]);
        int stop = Integer.parseInt(parts[STOP_PART]);
        String direction = parts[STRAND_PART];
        if (start > stop) {
            throwParsingException("Start-stop invalid");
        }
        return new GeneInfo(chromosome, extractId(parts[ATTRIBUTES_PART]),
                start, stop, parseStrand(direction));
    }

    protected GeneSequence parseSequence(String[] parts)
            throws GenomeFileParsingException {
        String type = parts[TYPE_PART];
        int start = Integer.parseInt(parts[START_PART]);
        int stop = Integer.parseInt(parts[STOP_PART]);
        String direction = parts[STRAND_PART];
        if (start > stop) {
            throwParsingException("Start-stop invalid");
        }
        return new GeneSequence(extractParent(parts[ATTRIBUTES_PART]),
                parseSequenceType(type), start, stop, parseStrand(direction));
    }

    private void processGene(Genome genome, GeneInfo gene) {
        genome.addGene(gene);
    }

    private void processSequence(Genome genome, String chromosome,
            GeneSequence sequence) throws GenomeFileParsingException {
        if (!genome.hasGene(sequence.getParentId())) {
            GeneInfo gene = new GeneInfo(chromosome, sequence.getParentId(),
                    sequence.getStart(), sequence.getStop(),
                    sequence.getDirection());
            genome.addGene(gene);
        }
        GeneInfo gene = genome.getGene(sequence.getParentId());
        if (gene.getDirection() != sequence.getDirection()) {
            throwParsingException("A sequence in gene " + gene.getId() + " has inconsistent direction");
        }
        if (gene.getStart() > sequence.getStart()) {
            throwParsingException("Start of sequence in gene " + gene.getId() + " overflows gene");
        }
        if (gene.getStop() < sequence.getStop()) {
            throwParsingException("Stop of sequence in gene " + gene.getId() + " overflows gene");
        }
        genome.getGene(sequence.getParentId()).addLocation(sequence);
    }

    private String extractId(String attributes)
            throws GenomeFileParsingException {
        Matcher m = ID_ATTRIBUTE_RE.matcher(attributes);
        if (m.matches()) {
            return m.group(1);
        }
        throwParsingException("Attribute ID not found");
        return null; // make compiler happy
    }

    private String extractParent(String attributes)
            throws GenomeFileParsingException {
        Matcher m = PARENT_ATTRIBUTE_RE.matcher(attributes);
        if (m.matches()) {
            return m.group(1);
        }
        throwParsingException("Attribute Parent not found");
        return null; // make compiler happy
    }
}
