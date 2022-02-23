package needle.genCallGraph;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;


public class Main {
    static class Args {
        @Parameter(names = "-i", description = "Input folder containing .jar files being analyzed", required = true)
        String inputFolder;

        @Parameter(names = "-o", description = "Output folder to which the graph .jsons will be emitted", required = true)
        String outputFolder;

        @Parameter(names = "-m", description = "Identifier of the main class containing the program's entry point", required = true)
        String mainClass;


        @Parameter(names = "--jar-filter",
                description = "A list of strings that one of which must appear in a jar filename in order to be included in the analysis. " +
                        "If empty, every jar in the input folder will be included.")
        List<String> jarPredicate = new ArrayList<>();

        @Parameter(names = "--edge-filter",
                description = "A list of strings that one of which must appear in an edge's src and dest identifier in order to be included. " +
                        "If empty, every edge in the callgraph will be included.")
        List<String> edgePredicate = new ArrayList<>();

        @Parameter(names = {"-h", "--help"}, help = true)
        boolean help = false;

    }
    public static void main(String[] argv) throws IOException {
        Args args = new Args();
        var cmdr = JCommander.newBuilder()
                .addObject(args)
                .build();
        cmdr.parse(argv);
        if (args.help) {
            cmdr.usage();
            return;
        }

        Predicate<Path> jarPredicate = (path) -> {
          if (args.jarPredicate.isEmpty()) {
              return true;
          }
          for (var part : args.jarPredicate) {
              if (path.getFileName().toString().contains(part)) {
                  return true;
              }
          }
          return false;
        };

        Predicate<Edge> edgePredicate = (edge) -> {
            if (args.edgePredicate.isEmpty()) {
                return true;
            }
            boolean sourceOk = false, tgtOk = false;
            for (var part: args.edgePredicate) {
                var sourceIdent = GenCallgraph.methodToIdentifier(edge.src());
                var tgtIdent = GenCallgraph.methodToIdentifier(edge.tgt());
                if (sourceIdent.contains(part)) {
                    sourceOk = true;
                }
                if (tgtIdent.contains(part)) {
                    tgtOk = true;
                }
                if (sourceOk && tgtOk) {
                    return true;
                }
            }
            return false;
        };

        GenCallgraph gen = new GenCallgraph(
                args.inputFolder,
                args.outputFolder,
                jarPredicate,
                edgePredicate,
                args.mainClass
        );
        gen.beginAnalysis();
    }
}
