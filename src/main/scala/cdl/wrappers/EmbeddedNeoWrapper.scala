/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.wrappers

import org.neo4j.cypher.{ ExecutionEngine, ExecutionResult }
import org.neo4j.graphdb.{ DynamicLabel, GraphDatabaseService, Transaction }
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import cdl.objects.UW
import org.neo4j.cypher.SyntaxException
import java.io.File
import cdl.parser.CDLParser
import cdl.objects.CDLDocument
import org.neo4j.graphdb.Node
import cdl.objects.Statement
import cdl.objects.Concept

/* 
 * Extending EmbeddedGraphDatabaseServiceProvider creates an instance of EmbeddedGraphDatabase,
 * which starts the server by calling InternalAbstractGraphDatabase#run()
 * 
 * @param accessPoint URI in case of REST endpoint, directory path in case of embedded Neo4j
 */
class EmbeddedNeoWrapper(val accessPoint: String) extends CDLNeoWrapper {
  private var db: GraphDatabaseService = _
  private var engine: ExecutionEngine = _

  def start() = {
    if (db == null) {
      registerShutdownHook
      db = new GraphDatabaseFactory().newEmbeddedDatabase(accessPoint)
      engine = new ExecutionEngine(db)

      /* 
       * Initialize indexes
       * 
       * Default configuration: MapUtil.stringMap( IndexManager.PROVIDER, "lucene", "type", "fulltext" ) )
       * More info: http://docs.neo4j.org/chunked/stable/indexing-create-advanced.html
       * 
       */
      try {
        val tx = db.beginTx
        val schema = db.schema
        schema.indexFor(NodeProperties.labels.UW).on(NodeProperties.Headword).create
        tx.success
      }
      logger.info("Embedded Neo4j located at "+accessPoint)
    }
  }

  def stop() = {
    if (db != null) {
      db.shutdown
      db = null
      logger.info("Detached Neo4j")
    }
  }

  def isConnected: Boolean = {
    //TODO: implement
    true
  }

  def getHyponyms(uw: UW): Iterator[UW] = {
    val ontology = fetchUWs(uw.toString)
    if (ontology.isEmpty) logger.info("Didn't find corresponding ontology for UW ["+uw.toString+"]")
    if (ontology.size > 1) logger.info("Multiple same ontologies in DB! "+ontology.mkString("{", ", ", "}"))
    var result: ExecutionResult = query("MATCH (:UW { hw: \""+uw.hw+"\" })-[:HYPO*1..20]->(hypo) RETURN hypo;")
    result.columnAs("y").map(row => rowToUW(row))
  }

  def query(q: String): ExecutionResult = {
    logger.info("Executing query:\n"+q)
    var tx: Transaction = null
    var result: ExecutionResult = null

    try {
      tx = db.beginTx
      result = engine.execute(q)
      tx.success
      logger.info("Query returned:\n"+result)
    } catch {
      case e: SyntaxException => logger.error("Invalid Cypher query"); tx.failure
    }
    return result
  }

  def cleanDB() {
    query("MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r)")
  }

  def fetchUWs(hw: String): Iterator[UW] = {
    var result = query("MATCH (uw:UW { hw: \""+hw+"\" } return uw")
    return result.map(row => rowToUW(row))
  }

  def importDocuments(docs: Array[File]) = {
    logger.info("Performing <importDocuments> for "+docs.length+" documents")
    var parsingTotal = 0L
    var dbAddTotal = 0L
    var time = 0L

    for (doc <- docs) {
      time = System.currentTimeMillis
      val parsed = (new CDLParser(doc, doc.getName)).parseDocument
      parsingTotal += System.currentTimeMillis - time
      time = System.currentTimeMillis
      addDocument(parsed)
      dbAddTotal += System.currentTimeMillis - time
    }
    logger.info("Parsed "+docs.length+" documents in: "+parsingTotal / 1000.0+"s")
    logger.info("Total db add time: "+dbAddTotal / 1000.0+"s")
  }
  
  def importOntology()

  private def rowToUW(row: Map[String, Any]): UW = new UW(row(NodeProperties.Headword).asInstanceOf[String], row(NodeProperties.Constraints).asInstanceOf[String])
  
  private def addDocument(parsedDoc: CDLDocument) = {
    logger.info("Adding document <"+parsedDoc.title + '>')
    for (entity <- parsedDoc.entities) {
      createDocumentNode.createRelationshipTo(unpackEntity(entity.asInstanceOf[Statement]), RelType.CONTAINS) 
    }
    logger.info("Finished adding a document")
  }
  
  private def createDocumentNode: Node = {
    return db.createNode(NodeProperties.labels.Document)
  }
  
  private def unpackEntity(stat: Statement): Node = {
    val topNode = createStatementNode(stat)
    if (stat.entities.nonEmpty) {
      var entities = LinkedHashMap[String, Node]()
      for (entity <- stat.entities) {
        if (entity.isInstanceOf[Concept]) {
          val concept = entity.asInstanceOf[Concept]
          val cNode = createConceptNode(concept)
          topNode --> CONTAINS --> cNode
          entities += concept.rlabel.toString -> cNode
        } else if (entity.isInstanceOf[Statement]) {
          val statement = entity.asInstanceOf[Statement]
          val sNode = unpackEntity(statement)
          entities += statement.rlabel.toString -> sNode
          topNode --> CONTAINS --> sNode
        }
      }
      for (arc <- stat.arcs) {
        if (entities.contains(arc.from.toString) && entities.contains(arc.to.toString)) {
          entities(arc.from.toString) --> RelTypes.toRelType(arc.relation) --> entities(arc.to.toString)
        } else {
          if (!entities.contains(arc.from.toString))
            logger.info("Did not find 'from' entity by id: "+arc.from.toString)
          if (!entities.contains(arc.to.toString))
            logger.info("Did not find 'to' entity by id: "+arc.to.toString)
        }
      }
    }
    return topNode
  }
  
  private def createStatementNode(statement: Statement): Node = {
    val node = db.createNode(NodeProperties.labels.Sentence)
    node(nodeProperties("nodeType")) = "Statement"
    node(nodeProperties("realizationLabel")) = statement.rlabel.toString
    return node
  }
}