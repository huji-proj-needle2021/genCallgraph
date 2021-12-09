import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Map;

import static org.neo4j.driver.Values.parameters;

public class Neo4jGraphWriter implements AutoCloseable {
    private final Driver driver;
    private final Session session;

    public Neo4jGraphWriter() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "12345"));
        session = driver.session();
        deleteGraph();
        createIndicesAndConstraints();
    }

    private void createIndicesAndConstraints() {
        System.out.println("Creating constraints and indices");

        session.writeTransaction(tx -> {
            tx.run("CREATE CONSTRAINT unique_method_sig IF NOT EXISTS FOR (m:Method) REQUIRE (m.sig) IS UNIQUE");
            tx.run("CREATE CONSTRAINT unique_class_def IF NOT EXISTS FOR (c:Class) REQUIRE (c.name, c.package) IS UNIQUE");

            tx.run("CREATE TEXT INDEX method_sig_index IF NOT EXISTS FOR (m:Method) ON (m.sig)");
            tx.run("CREATE TEXT INDEX method_name_index IF NOT EXISTS FOR (m:Method) ON (m.name)");
            tx.run("CREATE TEXT INDEX method_class_index IF NOT EXISTS FOR (m:Method) ON (m.class)");
            tx.run("CREATE TEXT INDEX method_package_index IF NOT EXISTS FOR (m:Method) ON (m.package)");

            tx.run("CREATE TEXT INDEX class_name_index IF NOT EXISTS FOR (c:Class) ON (c.name)");
            tx.run("CREATE TEXT INDEX class_package_index IF NOT EXISTS FOR (c:Class) ON (c.package)");
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

    public void deleteGraph() {
        System.out.println("Deleting current graph");
        session.writeTransaction(tx -> {
            tx.run("MATCH (c:Class) DETACH DELETE c");
            tx.run("MATCH (m:Method) DETACH DELETE m");
            return null;
        });
    }

    private final String ADD_EDGE_QUERY = String.join("\n",
                "MERGE (src:Method {sig: $srcProps.sig})",
                "ON CREATE SET src = $srcProps",
                "MERGE (tgt:Method {sig: $tgtProps.sig})",
                "ON CREATE SET tgt = $tgtProps",
                "MERGE (src)-[:CALL {kind: $kind}]->(tgt)");

    public void addEdge(Edge edge) {
        var srcProps = methodProps(edge.src());
        var tgtProps = methodProps(edge.tgt());
        session.writeTransaction(tx -> {
            tx.run(ADD_EDGE_QUERY,
                    parameters("kind", edge.kind().toString(),
                            "srcProps", srcProps,
                            "tgtProps", tgtProps));
           return null;
        });
    }

    public void createClassDependencyGraph() {
        session.writeTransaction(tx -> {
            var query = String.join("\n",
                    "MATCH (src:Method)-[:CALL]->(tgt:Method)",
                    "MERGE (srcClass:Class { name: src.class, package: src.package})",
                    "MERGE (tgtClass:Class { name: tgt.class, package: tgt.package})",
                    "MERGE (srcClass)-[:DEPENDS_ON]->(tgtClass)");
            tx.run(query);
            return null;
        });
    }

    @Override
    public void close() throws Exception {
        session.close();
        driver.close();
    }
}
