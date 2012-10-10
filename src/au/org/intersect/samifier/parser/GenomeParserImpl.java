package au.org.intersect.samifier.parser;


import au.org.intersect.samifier.domain.GeneInfo;
import au.org.intersect.samifier.domain.GeneSequence;
import au.org.intersect.samifier.domain.Genome;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenomeParserImpl implements GenomeParser
{

    public GenomeParserImpl()
    {

    }

    public Genome parseGenomeFile(File genomeFile)
        throws GenomeFileParsingException
    {
        try
        {
            return doParsing(genomeFile);
        }
        catch (IOException e)
        {
            throw new GenomeFileParsingException(e.getMessage());
        }

    }

    private Genome doParsing(File genomeFile) throws IOException {
        Genome genome = new Genome();

        BufferedReader reader = null;
        int lineNumber = 0;
        try{
            reader = new BufferedReader(new FileReader(genomeFile));

            String line;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;
                if (line.matches("^#.*$"))
                {
                    continue;
                }
                // chromosome, source, type, start, stop, score, strand, phase, attributes
                String[] parts = line.split("\\s+");
                if (parts.length < 9)
                {
                    //throw new GenomeFileParsingException("Line "+lineNumber+": not in expected format");
                    continue;
                }
                String type = parts[2];
                if (type == null)
                {
                    continue;
                }
                Matcher typeMatcher = GeneSequence.SEQUENCE_RE.matcher(type);
                if (typeMatcher.matches())
                {
                    processSequence(parts, genome);
                }
            }
        }
        finally
        {
            if (reader != null)
            {
                reader.close();
            }
        }
        return genome;
    }

    private void processSequence(String[] parts, Genome genome)
    {
        String chromosome = parts[0];
        String type = parts[2];
        String orderedLocusName = extractOrderedLocusName(parts[8]);
        int start = Integer.parseInt(parts[3]);
        int stop = Integer.parseInt(parts[4]);
        String direction = parts[6];
        GeneInfo gene;
        if (genome.hasGene(orderedLocusName))
        {
            gene = genome.getGene(orderedLocusName);
            if (gene == null)
            {
                System.err.println(orderedLocusName + " not found in genome object");
                return;
            }
            GeneSequence seq = new GeneSequence(type, start, stop, direction);
            gene.addLocation(seq);
        }
        else
        {
            gene = new GeneInfo(chromosome, start, stop, direction);
            genome.addGene(orderedLocusName, gene);
        }
    }

    private String extractOrderedLocusName(String attributes)
    {
        Pattern olnPattern = Pattern.compile(".*Name=([^_;]+)[_;].*");
        Matcher m = olnPattern.matcher(attributes);
        if (m.matches())
        {
            return m.group(1);
        }
        return null;
    }
}
