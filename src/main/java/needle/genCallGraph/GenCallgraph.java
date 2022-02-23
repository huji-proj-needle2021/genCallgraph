package needle.genCallGraph;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class GenCallgraph {
    public final String inputFolder; // = "toAnalyze";
    public final String outputFolder;
    public final List<String> inputJars;
    public final String mainClass; // = "jadx.gui.JadxGUI";
    public final Predicate<Edge> edgePredicate;

    public final List<String> EXCLUDES = List.of(
            "java.*",
            "org.slf4j.*",
            "com.sun.*",
            "com.google.*"
    );

    public GenCallgraph(String inputFolder,
                        String outputFolder,
                        Predicate<Path> jarFilter,
                        Predicate<Edge> edgePredicate,
                        String mainClass) throws IOException {
        this.inputFolder = inputFolder;
        this.outputFolder = outputFolder;
        this.mainClass = mainClass;
        this.inputJars = Files.list(Paths.get(this.inputFolder))
                .map(Path::toAbsolutePath)
                .filter(p -> jarFilter.test(p.getFileName()) && p.getFileName().toString().toLowerCase().endsWith(".jar"))
                .map(Path::toString)
                .collect(Collectors.toList());
        if (this.inputJars.isEmpty()) {
            throw new IOException("Couldn't find any .jar files matching predicate at " + inputFolder);
        }
        this.edgePredicate = edgePredicate;

    }

    public static String methodToIdentifier(SootMethod method) {
        return  method.getDeclaringClass().getName() + "." + method.getName();
    }

    public void beginAnalysis() throws IOException {

        Options.v().set_soot_classpath(inputFolder);
        Options.v().set_app(true);
        Options.v().set_whole_program(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_process_dir(inputJars);
        Options.v().set_main_class(mainClass);
        Options.v().set_exclude(EXCLUDES);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_no_bodies_for_excluded(true);

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTrans", new SceneTransformer() {
            @Override
            protected void internalTransform(String phaseName, Map options) {
                CallGraph cg = Scene.v().getCallGraph();
                int cgSize = cg.size();
                int processed = 0;
                int lastPercentageMilestone = -1;
                System.out.println("Number of reachable methods is " + Scene.v().getReachableMethods().size());
                System.out.println("Beginning to process call graph of size " + cgSize);
                try (var writer = new JSONGraphWriter(outputFolder))
                {
                    for (Edge edge: cg) {
                        if (edgePredicate.test(edge)) {
                            writer.addEdge(edge);
                        }
                        ++processed;
                        var percentage = processed * 100 / cgSize;
                        if (percentage != lastPercentageMilestone &&  percentage % 10 == 0) {
                            System.out.println("Processed " + percentage + "% of all edges(" + processed + "/" + cgSize + ")");
                            lastPercentageMilestone = percentage;
                        }
                    }
                    System.out.println("Done creating function call graph");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        }));


        System.out.println("Loading necessary classes, input jars are: " + inputJars);
        Scene.v().loadNecessaryClasses();
        System.out.println("Running packs");
        PackManager.v().runPacks();
    }
}
