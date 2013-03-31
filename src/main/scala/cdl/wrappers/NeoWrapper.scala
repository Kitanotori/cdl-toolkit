/**
 * @author Petri Kivikangas
 * @date 6.2.2012
 *
 */
package cdl.wrappers

import java.io.File
import java.net.URI

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.immutable.TreeSet
import scala.collection.mutable.LinkedHashMap

import org.apache.lucene.index.Term
import org.apache.lucene.queryParser.QueryParser
import org.apache.lucene.search.WildcardQuery
import org.neo4j.cypher.{ ExecutionEngine, ExecutionResult, SyntaxException }
import org.neo4j.graphdb.{ Node, PropertyContainer }
import org.neo4j.graphdb.index.{ Index, IndexManager }
import org.neo4j.scala.{ Neo4jIndexProvider, Neo4jWrapper }
import org.slf4j.LoggerFactory

import com.sun.jersey.api.client.ClientHandlerException

import cdl.editor.Config
import cdl.objects.{ CDLDocument, Concept, Statement, UW }
import cdl.parser.{ CDLParser, OntologyParser }
import cdl.parser.OntologyParser.UwNode
import cdl.wrappers.RelTypes.{ CONTAINS, HYPO, ONTOLOGY, conv }

class NeoWrapperException(reason: String) extends Exception(reason)

trait CDLNeoLightWrapper {
  def getHyponyms(uw: UW): List[String]
  def query(q: String): ExecutionResult
  def cleanDB(): Map[String, Any]
  def fetchUWs(head: String): Array[UW]
  def importDocuments(docs: Array[File])
  def isConnected: Boolean // should return true if the wrapper is able to connect to database
  def stop() // call when discarding the wrapper object
  def start() // call when starting to use the wrapper object
  def importOntology()
  def accessPoint: String // For embedded-only wrappers this is the filesystem path. For REST-based wrappers this is the uri
}

trait CDLNeoWrapper extends Neo4jWrapper with Neo4jIndexProvider with CDLNeoLightWrapper {
  import NeoWrapper.nodeProperties

  protected val logger = LoggerFactory.getLogger(this.getClass)

  /* These should be defined by the Neo wrapper implementations */
  var execEngine: ExecutionEngine
  def accessPoint: String
  def start()
  def stop()
  def isConnected: Boolean

  def getHyponyms(uw: UW): List[String] = {
    val ontology = fetchUWs(uw.toString)
    if (ontology.isEmpty) logger.info("Didn't find corresponding ontology for UW ["+uw.uw + ']')
    if (ontology.size > 1) logger.info("Multiple same ontologies in DB! <"+ontology.toString)
    var hypos = List[String]()
    val res = query("START x=node:uws(uw='"+uw.uw+"') MATCH x-[:HYPO*1..20]->y RETURN y")
    val i: Iterator[Node] = res.columnAs("y")
    while (i.hasNext) {
      hypos ::= i.next.getProperty(NeoWrapper.nodeProperties("universalWord")).asInstanceOf[String]
    }
    return hypos
  }

  def query(q: String): ExecutionResult = {
    logger.info("Executing query:\n"+q)
    var result: ExecutionResult = null
    try {
      result = execEngine.execute(q)
    } catch {
      case e: SyntaxException => logger.error("Invalid Cypher query"); return null
    }
    if (result != null) {
      logger.info("Query returned:\n"+result)
      return result
    }
    return null
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

  def fetchUWs(head: String): Array[UW] = {
    val uwOrdering = Ordering.fromLessThan[UW](_.toString > _.toString)
    var words = TreeSet.empty[UW](uwOrdering)
    val i = uws.query(new WildcardQuery(new Term(nodeProperties("universalWord"), sanitize(head) + '*'))).iterator // Fetch UWs starting with <head>
    while (i.hasNext) {
      words += new UW(i.next.getProperty(nodeProperties("universalWord")).asInstanceOf[String])
    }
    return words.toArray
  }

  def addDocument(parsedDoc: CDLDocument) = {
    logger.info("Adding document <"+parsedDoc.title + '>')
    val documentNode = createDocumentNode(parsedDoc)
    rootNode --> CONTAINS --> documentNode // Add CDL documents to the root node
    for (entity <- parsedDoc.entities) {
      documentNode --> CONTAINS --> unpackEntity(entity.asInstanceOf[Statement])
    }
    logger.info("Finished adding a document")
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

  private def createDocumentNode(doc: CDLDocument): Node = {
    val node = createNode(ds)
    node(nodeProperties("nodeType")) = "Document"
    return node
  }

  private def createStatementNode(statement: Statement): Node = {
    val node = createNode(ds)
    node(nodeProperties("nodeType")) = "Statement"
    node(nodeProperties("realizationLabel")) = statement.rlabel.toString
    return node
  }

  private def createConceptNode(concept: Concept): Node = {
    val node = createNode(ds)
    node(nodeProperties("nodeType")) = "Concept"
    node(nodeProperties("realizationLabel")) = concept.rlabel.toString
    node(nodeProperties("universalWord")) = concept.uw
    node(nodeProperties("attributes")) = concept.attributes.toArray[String]
    concepts += (node, nodeProperties("universalWord"), concept.uw)
    val ontology = uws.get(nodeProperties("universalWord"), concept.uw)
    if (ontology.size > 1) {
      logger.error("uws index contained multiple same UWs. UW: "+concept.uw)
    } else {
      val o = ontology.getSingle
      if (o == null) {
        logger.info("Couldn't find ontology: "+concept.uw)
      } else {
        node --> ONTOLOGY --> o
        logger.info("Added ("+node.getId+") ~> ("+o.getId+") UW: "+concept.uw)
      }
    }
    return node
  }

  /* Imports UNL Ontology */
  def importOntology() = {
    val parsed = OntologyParser.parse(Config.getProperty("unlOntologyPath").get)
    logger.info("Importing ontologies..")
    val time = System.currentTimeMillis
    val top = doImport(parsed)
    rootNode --> CONTAINS --> top
    logger.info("Imported ontologies in "+(System.currentTimeMillis - time) / 1000.0+"s")
  }

  def doImport(n: UwNode): Node = {
    val current = createUWNode(n.text)
    if (n.child.nonEmpty) {
      n.child.foreach(c => current --> HYPO --> doImport(c))
    }
    return current
  }

  private def createUWNode(uw: String): Node = {
    val node = createNode(ds)
    node(nodeProperties("nodeType")) = "UW"
    node(nodeProperties("universalWord")) = uw
    uws += (node, nodeProperties("universalWord"), uw)
    return node
  }

  /* 
   * This is too slow to use in practice. 
   * Use e.g. neo4j-clean-remote-db-addon 
   * server plugin for cleaning db. 
   */
  def cleanDB(): Map[String, Any] = {
    var result = clearIndex
    result ++= removeNodes(Long.MaxValue)
    return result
  }

  private def clearIndex(): Map[String, Any] = {
    var r = Map[String, Any]()
    val indexManager: IndexManager = ds.gds.index
    r += "node-indexes" -> indexManager.nodeIndexNames.toList
    r += "relationship-indexes" -> indexManager.relationshipIndexNames.toList
    try {
      for (ix <- indexManager.nodeIndexNames) {
        getMutableIndex(indexManager.forNodes(ix)).delete
      }
      for (ix <- indexManager.relationshipIndexNames) {
        getMutableIndex(indexManager.forRelationships(ix)).delete
      }
    } catch {
      case ex: UnsupportedOperationException => throw new RuntimeException("Implementation detail assumption failed for cleaning readonly indexes, please make sure that the version of this extension and the Neo4j server align")
    }
    return r
  }

  private def removeNodes(maxNodesToDelete: Long): Map[String, Any] = {
    import org.neo4j.tooling.GlobalGraphOperations
    var r = Map[String, Any]()
    var nodes = 0L
    var relationships = 0L
    for (node <- GlobalGraphOperations.at(ds.gds).getAllNodes if nodes < maxNodesToDelete) {
      for (rel <- node.getRelationships) {
        rel.delete
        relationships += 1
      }
      if (!node.equals(rootNode)) { // Don't delete the root node!
        node.delete
        nodes += 1
      }
    }
    r += "maxNodesToDelete" -> maxNodesToDelete
    r += "nodes" -> nodes
    r += "relationships" -> relationships
    return r
  }

  private def getMutableIndex[T <: PropertyContainer](index: Index[T]): Index[T] = {
    val indexClass = index.getClass
    if (indexClass.getName.endsWith("ReadOnlyIndexToIndexAdapter")) {
      try {
        val delegateIndexField = indexClass.getDeclaredField("delegate")
        delegateIndexField.setAccessible(true)
        return delegateIndexField.get(index).asInstanceOf[Index[T]]
      } catch {
        case ex: Exception => throw new UnsupportedOperationException(ex)
      }
    } else {
      return index
    }
  }

  protected def sanitize(query: String): String = QueryParser.escape(query)

  protected def registerShutdownHook() = {
    // Registers a shutdown hook for the Neo4j instance so that it
    // shuts down nicely when the VM exits (even if you "Ctrl-C" the
    // running example before it's completed)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run = NeoWrapper.stop
    })
  }

  /* Define indexes */
  override def NodeIndexConfig =
    ("concepts", Some(Map("provider" -> "lucene", "type" -> "exact"))) ::
      ("documents", Some(Map("provider" -> "lucene", "type" -> "exact"))) ::
      ("uws", Some(Map("provider" -> "lucene", "type" -> "exact"))) :: Nil

  /* Index accessor objects */
  lazy val concepts = getNodeIndex("concepts").get
  lazy val documents = getNodeIndex("documents").get
  lazy val uws = getNodeIndex("uws").get
  
  lazy val rootNode: Node = ds.gds.getNodeById(0)
}

object NeoWrapper {
  protected val logger = LoggerFactory.getLogger(this.getClass)
  var impl: CDLNeoLightWrapper = null

  def toggleRestWrapper() = {
    logger.info("Toggling REST API wrapper...")
    stop
    Config.getProperty("restNeoURI") match {
      case Some(uri) =>
        try {
          impl = new RestNeoWrapper(new URI(uri))
          if (impl.isConnected) {
            logger.info("Toggled REST API wrapper for "+uri)
            start
          } else {
            logger.error("Unable to toggle REST API wrapper for "+uri)
            impl = null
          }
        } catch {
          case ex: ClientHandlerException => logger.error("Could not create RestNeoWrapper for "+uri+": "+ex.getMessage); impl = null
        }
      case None => logger.info("Unable to toggle RestNeoWrapper")
    }
  }

  def toggleRestBatchWrapper() = {
    // TODO: implement
  }

  def toggleEmbeddedBatchWrapper() = {
    logger.info("Toggling EmbeddedBatchInserter...")
    stop
    Config.getProperty("embeddedNeoPath") match {
      case Some(path) =>
        logger.info("Using configuration: embeddedNeoPath = "+path)
        impl = new EmbeddedBatchInserter(path)
        start
        logger.info("Toggled EmbeddedBatchInserter")
      case None => logger.info("Unable to toggle EmbeddedBatchInserter")
    }
  }

  def toggleEmbeddedWrapper() = {
    logger.info("Toggling EmbeddedNeoWrapper...")
    stop
    Config.getProperty("embeddedNeoPath") match {
      case Some(path) =>
        logger.info("Using configuration: embeddedNeoPath = "+path)
        impl = new EmbeddedNeoWrapper(path)
        start
        logger.info("Toggled EmbeddedNeoWrapper")
      case None => logger.info("Unable to toggle EmbeddedNeoWrapper")
    }
  }

  /*
   * Map containing acceptable node property names.
   * The values are the property names stored in Neo4j.
   */
  val nodeProperties = Map(
    "nodeType" -> "cdlType",
    "headword" -> "hw",
    "attributes" -> "attrs",
    "realizationLabel" -> "rlabel",
    "universalWord" -> "uw",
    "attributeName" -> "attr",
    "constraints" -> "cons",
    "documentTitle" -> "title")

  /* Direct the db calls to wrapper implementations */
  def getHyponyms(uw: UW): List[String] = impl.getHyponyms(uw)
  def query(q: String): ExecutionResult = impl.query(q)
  def cleanDB() = impl.cleanDB
  def fetchUWs(head: String): Array[UW] = impl.fetchUWs(head)
  def importDocuments(docs: Array[File]) = impl.importDocuments(docs)
  def isConnected: Boolean = impl.isConnected
  def stop() = if (impl != null) impl.stop
  def start() = impl.start
  def importOntology() = impl.importOntology

}
