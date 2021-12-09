import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.jimple.toolkits.callgraph.*;
import soot.options.CHAOptions;
import soot.options.Options;


public class GenCallgraph {
    static final String INPUT_FOLDER = "toAnalyze";
    static final String INPUT_JAR = String.valueOf(Paths.get(INPUT_FOLDER, "jadx-gui-dev.jar").toAbsolutePath());
    static final List<String> INPUT_JARS;
    static final String MAIN_CLASS = "jadx.gui.JadxGUI";

    static final List<String> EXCLUDES = List.of(
            "java.*",
            "org.slf4j.*",
            "com.sun.*",
            "com.google.*"
    );

    static {
        List<String> tempInputJars;
        try {
            tempInputJars = Files.list(Paths.get(INPUT_FOLDER))
                        .map(Path::toAbsolutePath)
                        .filter(p -> p.getFileName().toString().startsWith("jadx"))
                        .map(Path::toString)
                        .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            tempInputJars = null;
            System.exit(1);
        }
        INPUT_JARS = tempInputJars;
    }


    static boolean processEdge(Edge edge) {
        var srcClass = edge.src().getDeclaringClass().getName();
        var tgtClass = edge.tgt().getDeclaringClass().getName();
//        return true;
        return srcClass.startsWith("jadx") && tgtClass.startsWith("jadx");
    }

    public static void main(String[] args) {

        Options.v().set_soot_classpath(INPUT_FOLDER);
        Options.v().set_app(true);
        Options.v().set_whole_program(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_process_dir(INPUT_JARS);
        Options.v().set_main_class(MAIN_CLASS);
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
                try (var writer = new GraphWriter()) {
                    for (Edge edge: cg) {
                        if (processEdge(edge)) {
                            writer.addEdge(edge);
                        }
                        ++processed;
                        var percentage = processed * 100 / cgSize;
                        if (percentage != lastPercentageMilestone &&  percentage % 10 == 0) {
                            System.out.println("Processed " + percentage + "% of all edges(" + processed + "/" + cgSize + ")");
                            lastPercentageMilestone = percentage;
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        }));


        System.out.println("Loading necessary classes, input jars are: " + INPUT_JARS);
        Scene.v().loadNecessaryClasses();
        System.out.println("Running packs");
        PackManager.v().runPacks();
        System.out.println("Done");
    }
}