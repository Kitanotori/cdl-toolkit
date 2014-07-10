/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.neo4j.wrappers

import java.io.File
import java.net.URI

import scala.collection.mutable.LinkedHashMap

import org.neo4j.cypher.{ ExecutionEngine, ExecutionResult, SyntaxException }
import org.neo4j.graphdb.{ GraphDatabaseService, Node, Transaction }
import org.neo4j.graphdb.factory.GraphDatabaseFactory

import cdl.neo4j.wrappers.RelType.conv
import cdl.objects.{ CDLDocument, ComplexEntity, Constraint, Entity, UW }
import cdl.parser.CDLParser

/* 
 * Extending EmbeddedGraphDatabaseServiceProvider creates an instance of EmbeddedGraphDatabase,
 * which starts the server by calling InternalAbstractGraphDatabase#run()
 * 
 * @param accessPoint URI in case of REST endpoint, directory path in case of embedded Neo4j
 */
class EmbeddedNeoWrapper(uri: String) extends CDLNeoWrapper {
  private var db: GraphDatabaseService = _
  private var engine: ExecutionEngine = _

  def start() = {
    if (db == null) {
      neoURI = new URI(uri)
      db = new GraphDatabaseFactory().newEmbeddedDatabase(getNeoURI)
      engine = new ExecutionEngine(db)

      /* 
       * Initialize indexes
       * 
       * Default configuration: MapUtil.stringMap( IndexManager.PROVIDER, "lucene", "type", "fulltext" ) )
       * More info: http://docs.neo4j.org/chunked/stable/indexing-create-advanced.html
       * 
       */
      try {
        val tx = db.beginTx()
        val schema = db.schema
        schema.indexFor(NodeProperties.labels.UW).on(NodeProperties.Headword).create()
        tx.success()
      }
      log.info("Embedded Neo4j located at URI " + getNeoURI)
      registerShutdownHook()
    }
  }

  def stop() = {
    if (db != null) {
      db.shutdown()
      db = null
      log.info("Detached Neo4j located at URI " + getNeoURI)
    }
  }

  def isConnected: Boolean = {
    //TODO: implement
    true
  }

  def getHyponyms(uw: UW): Iterator[UW] = {
    val ontology = fetchUWs(uw.toString)
    if (ontology.isEmpty) log.info("Didn't find corresponding ontology for UW [" + uw.toString() + "]")
    if (ontology.size > 1) log.info("Multiple same ontologies in DB! " + ontology.mkString("{", ", ", "}"))
    var result: ExecutionResult = query("MATCH (:UW { hw: \"" + uw.hw + "\" })-[:HYPO*1..20]->(hypo) RETURN hypo;")
    result.columnAs("y").map(row => rowToUW(row))
  }

  def query(q: String): ExecutionResult = {
    log.info("Executing query:\n" + q)
    var tx: Transaction = null
    var result: ExecutionResult = null

    try {
      tx = db.beginTx()
      result = engine.execute(q)
      tx.success()
      log.info("Query returned:\n" + result)
    } catch {
      case e: SyntaxException => log.error("Invalid Cypher query"); tx.failure()
    }
    return result
  }

  def cleanDB() {
    query("MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r)")
  }

  def fetchUWs(hw: String): Iterator[UW] = {
    var result = query("MATCH (uw:UW { hw: \"" + hw + "\" } return uw")
    return result.map(row => rowToUW(row))
  }

  def importDocuments(docs: Array[File]) = {
    log.info("Performing <importDocuments> for " + docs.length + " documents")
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
    log.info("Parsed " + docs.length + " documents in: " + parsingTotal / 1000.0 + "s")
    log.info("Total db add time: " + dbAddTotal / 1000.0 + "s")
  }

  def importOntology() = {
    log.warn("importOntology() not supported for EmbeddedNeoWrapper Neo4j. Use EmbeddedBatchInserter.")
  }

  private def rowToUW(row: Map[String, Any]): UW = {
    val hw = row(NodeProperties.Headword).asInstanceOf[String]
    val consString = row(NodeProperties.Constraints).asInstanceOf[String]
    val cons = CDLParser.parseConstraints(consString)
    new UW(hw, cons)
  }

  private def addDocument(parsedDoc: CDLDocument) = {
    try {
      val tx = db.beginTx()
      val docNode = createDocumentNode()
      parsedDoc.entities.foreach(e => docNode.createRelationshipTo(unpackEntity(parsedDoc), RelType.CONTAINS))
      tx.success()
    }
  }

  private def createDocumentNode(): Node = {
    return db.createNode(NodeProperties.labels.Document)
  }

  private def unpackEntity(entity: ComplexEntity): Node = {
    val outerEntity = createEntityNode(entity)
    if (entity.entities.nonEmpty) {
      val entities = LinkedHashMap[String, Node]()
      for (e <- entity.entities) {
        if (e.isInstanceOf[UW]) {
          val elemNode = createEntityNode(e.asInstanceOf[UW])
          outerEntity.createRelationshipTo(elemNode, RelType.CONTAINS)
          entities += e.rlabel.toString -> elemNode
        } else if (e.isInstanceOf[ComplexEntity]) {
          val innerEntity = unpackEntity(e.asInstanceOf[ComplexEntity])
          entities += e.rlabel.toString -> innerEntity
          outerEntity.createRelationshipTo(innerEntity, RelType.CONTAINS)
        }
      }
      for (arc <- entity.relations) {
        if (entities.contains(arc.from.toString) && entities.contains(arc.to.toString)) {
          entities(arc.from.toString).createRelationshipTo(entities(arc.to.toString),
            RelType.toRelType(arc.relation.toString))
        } else {
          if (!entities.contains(arc.from.toString))
            log.warn("Did not find 'from' entity by id: " + arc.from.toString)
          if (!entities.contains(arc.to.toString))
            log.warn("Did not find 'to' entity by id: " + arc.to.toString)
        }
      }
    }
    return outerEntity
  }

  private def createEntityNode(entity: Entity): Node = entity match {
    case e: UW => {
      val elemNode = db.createNode(NodeProperties.labels.Concept)
      elemNode.setProperty(NodeProperties.RealizationLabel, e.rlabel.toString)
      elemNode.setProperty(NodeProperties.Headword, e.hw)
      elemNode.setProperty(NodeProperties.UniversalWord, e.baseUW)
      elemNode.setProperty(NodeProperties.Constraints, Constraint.getConsStr(e.cons))
      return elemNode
    }
    case e: CDLDocument => {
      val docNode = db.createNode(NodeProperties.labels.Document)
      docNode.setProperty(NodeProperties.RealizationLabel, e.rlabel)
      return docNode
    }
    case e: ComplexEntity => {
      val sentNode = db.createNode(NodeProperties.labels.Sentence)
      sentNode.setProperty(NodeProperties.RealizationLabel, e.rlabel)
      return sentNode
    }
  }
}