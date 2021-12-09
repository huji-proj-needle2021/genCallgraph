import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Map;

import static org.neo4j.driver.Values.parameters;

public class GraphWriter implements AutoCloseable {
    private final Driver driver;
    private final Session session;

    public GraphWriter() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "12345"));
        session = driver.session();
        createIndices();
    }

    private void createIndices() {
        session.writeTransaction(tx -> {
            tx.run("CREATE TEXT INDEX method_sig_index IF NOT EXISTS FOR (m:Method) ON (m.sig)");
            tx.run("CREATE TEXT INDEX method_name_index IF NOT EXISTS FOR (m:Method) ON (m.name)");
            tx.run("CREATE TEXT INDEX method_class_index IF NOT EXISTS FOR (m:Method) ON (m.class)");
            tx.run("CREATE TEXT INDEX method_package_index IF NOT EXISTS FOR (m:Method) ON (m.package)");
            return null;
        });
    }

    private Map<String, Object> methodProps(SootMethod method) {
        return Map.of(
                "name", method.getName(),
                "class", method.getDeclaringClass().getShortName(),
                "package", method.getDeclaringClass().getPackageName(),
                "sig", method.getSignature()
        );
    }

    public void addEdge(Edge edge) {
        var srcProps = methodProps(edge.src());
        var tgtProps = methodProps(edge.tgt());
        session.writeTransaction(tx -> {
            tx.run("MERGE (src:Method {sig: $srcProps.sig})\n" +
                    "ON CREATE SET src = $srcProps\n" +
                    "MERGE (tgt:Method {sig: $tgtProps.sig})\n" +
                    "ON CREATE SET tgt = $tgtProps\n" +
                    "MERGE (src)-[:CALL {kind: $kind}]->(tgt)",
                    parameters("kind", edge.kind().toString(),
                            "srcProps", srcProps,
                            "tgtProps", tgtProps));
           return null;
        });
    }

    @Override
    public void close() throws Exception {
        session.close();
        driver.close();
    }
}
