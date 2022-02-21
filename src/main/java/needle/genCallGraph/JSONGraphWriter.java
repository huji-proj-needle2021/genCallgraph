package needle.genCallGraph;

import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;



public class JSONGraphWriter implements AutoCloseable {

    static final String IDENTIFIER_EDGELIST_FILE = "edges.json";
    static final String IDENTIFIER_METHOD_MAPPING = "mapping.json";

    final BufferedWriter edgeFile;
    final BufferedWriter mappingFile;

    final JsonGenerator edgeGen;
    final JsonGenerator mapGen;

    final HashSet<String> seenIdentifiers;

    public JSONGraphWriter(String outputFolder) throws IOException {
        File folderF = new File(outputFolder);
        if (!folderF.mkdirs()) {
            throw new IOException("Couldn't create output folder at " + outputFolder);
        }

        var edgeFilepath = Paths.get(outputFolder, IDENTIFIER_EDGELIST_FILE);
        var mapFilepath = Paths.get(outputFolder, IDENTIFIER_METHOD_MAPPING);

        edgeFile = Files.newBufferedWriter(edgeFilepath, StandardCharsets.UTF_8);
        mappingFile = Files.newBufferedWriter(mapFilepath, StandardCharsets.UTF_8);

        var fac = new JsonFactory();
        edgeGen = fac.createGenerator(edgeFile);
        mapGen = fac.createGenerator(mappingFile);

        edgeGen.writeStartArray();
        mapGen.writeStartObject();

        seenIdentifiers = new HashSet<>();

    }

    @Override
    public void close() throws Exception {
        edgeGen.writeEndArray();
        mapGen.writeEndObject();

        edgeGen.close();
        mapGen.close();
        edgeFile.close();
        mappingFile.close();
    }

    private void addMethod(SootMethod method) throws IOException {
        if (seenIdentifiers.contains(method.getSignature())) {
            return;
        }
        mapGen.writeObjectFieldStart(method.getSignature());
        mapGen.writeStringField("method", method.getName());
        mapGen.writeStringField("class", method.getDeclaringClass().getShortName());
        mapGen.writeStringField("package", method.getDeclaringClass().getPackageName());
        mapGen.writeEndObject();
    }

    public void addEdge(Edge edge) throws IOException {
        SootMethod src = edge.src();
        SootMethod tgt = edge.tgt();
        addMethod(src);
        addMethod(tgt);

        edgeGen.writeStartObject();
        edgeGen.writeStringField("src", src.getSignature());
        edgeGen.writeStringField("tgt", tgt.getSignature());
        edgeGen.writeStringField("kind", edge.kind().name());
        edgeGen.writeEndObject();
    }
}
