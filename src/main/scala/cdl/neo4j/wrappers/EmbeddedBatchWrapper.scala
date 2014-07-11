/**
 * @author Petri Kivikangas
 * @date 23.4.2012
 *
 */
package cdl.neo4j.wrappers

import java.io.File
import java.net.{ URI, URISyntaxException }
import java.nio.file.{ Files, Paths }

import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.mutable.LinkedHashMap

import org.neo4j.cypher.ExecutionResult
import org.neo4j.helpers.collection.MapUtil
import org.neo4j.index.lucene.unsafe.batchinsert.LuceneBatchInserterIndexProvider
import org.neo4j.unsafe.batchinsert.{ BatchInserter, BatchInserterIndex, BatchInserterIndexProvider, BatchInserters }

import cdl.editor.Config
import cdl.neo4j.wrappers.RelType.conv
import cdl.objects.{ CDLDocument, ComplexEntity, Constraint, Entity, UW }
import cdl.parser.{ CDLParser, OntologyParser }
import cdl.parser.OntologyParser.UwNode

class EmbeddedBatchWrapper(uri: String) extends CDLNeoWrapper {
  private var inserter: BatchInserter = null
  private var indexProvider: BatchInserterIndexProvider = null
  private var uws: BatchInserterIndex = null
  private var elems: BatchInserterIndex = null
  private var sents: BatchInserterIndex = null

  /*
   * No need to implement these for batch mode
   */
  def getHyponyms(uw: UW): Iterator[UW] = null
  def query(q: String): ExecutionResult = null
  def cleanDB(): Boolean = false
  def fetchUWs(hw: String): Iterator[UW] = null

  def importDocuments(docs: Array[File]): Boolean = {
    log.info("Importing "+docs.length+" documents in batch mode")
    var parsingTotal = 0L
    var dbAddTotal = 0L
    var time = 0L

    try {
      for (doc <- docs) {
        time = System.currentTimeMillis
        val parsed = (new CDLParser(doc, doc.getName)).parseDocument
        parsingTotal += System.currentTimeMillis - time
        time = System.currentTimeMillis
        addDocument(parsed)
        dbAddTotal += System.currentTimeMillis - time
      }
    } catch { case e: Exception => log.error("Failed to import documents", e); return false }
    log.info("Parsed "+docs.length+" documents in: "+parsingTotal / 1000.0+"s")
    log.info("Total db add time: "+dbAddTotal / 1000.0+"s")
    return true
  }

  /* Imports UNL Ontology */
  def importOntology(): Boolean = {
    try {
      val parsed = OntologyParser.parse(Config.getProperty("unlOntologyPath").get)
      log.info("Importing ontologies..")
      val time = System.currentTimeMillis
      val top = doImport(parsed)
      log.info("Imported ontologies in "+(System.currentTimeMillis - time) / 1000.0+"s")
      return true
    } catch { case e: Exception => log.error("Import failed", e); return false }
  }

  def doImport(n: UwNode): Long = {
    val current = createUWNode(n.text)
    if (n.child.nonEmpty) {
      n.child.foreach(c => inserter.createRelationship(current, doImport(c), RelType.HYPO, null))
    }
    return current
  }

  private def createUWNode(uw: String): Long = {
    val props = Map[String, String](NodeProperties.UniversalWord -> uw)
    return inserter.createNode(props, NodeProperties.labels.UW)
  }

  private def addDocument(parsedDoc: CDLDocument) = {
    val docNode = createDocumentNode()
    parsedDoc.entities.foreach(e => inserter.createRelationship(docNode, unpackEntity(parsedDoc), RelType.CONTAINS, null))
  }

  private def unpackEntity(entity: ComplexEntity): Long = {
    val outerEntity = createEntityNode(entity)
    if (entity.entities.nonEmpty) {
      val entities = LinkedHashMap[String, Long]()
      for (e <- entity.entities) {
        if (e.isInstanceOf[UW]) {
          val elemNode = createEntityNode(e.asInstanceOf[UW])
          inserter.createRelationship(outerEntity, elemNode, RelType.CONTAINS, null)
          entities += e.rlabel.toString -> elemNode
        } else if (e.isInstanceOf[ComplexEntity]) {
          val innerEntity = unpackEntity(e.asInstanceOf[ComplexEntity])
          entities += e.rlabel.toString -> innerEntity
          inserter.createRelationship(outerEntity, innerEntity, RelType.CONTAINS, null)
        }
      }
      for (arc <- entity.relations) {
        if (entities.contains(arc.from.toString) && entities.contains(arc.to.toString)) {
          inserter.createRelationship(entities(arc.from.toString), entities(arc.to.toString), RelType.toRelType(arc.relation.toString), null)
        } else {
          if (!entities.contains(arc.from.toString))
            log.warn("Did not find 'from' entity by id: "+arc.from.toString)
          if (!entities.contains(arc.to.toString))
            log.warn("Did not find 'to' entity by id: "+arc.to.toString)
        }
      }
    }
    return outerEntity
  }

  private def createDocumentNode(): Long = {
    return inserter.createNode(null, NodeProperties.labels.Document)
  }

  private def createEntityNode(entity: Entity): Long = entity match {
    case e: UW => {
      val props = Map(NodeProperties.RealizationLabel -> e.rlabel.toString,
        NodeProperties.Headword -> e.hw,
        NodeProperties.UniversalWord -> e.baseUW,
        NodeProperties.Constraints -> Constraint.getConsStr(e.cons)
      )
      return inserter.createNode(props, NodeProperties.labels.Concept)
    }
    case e: CDLDocument => {
      return inserter.createNode(Map(NodeProperties.RealizationLabel -> e.rlabel.toString), NodeProperties.labels.Document)
    }
    case e: ComplexEntity => {
      return inserter.createNode(Map(NodeProperties.RealizationLabel -> e.rlabel.toString), NodeProperties.labels.Sentence)
    }
  }

  /* 
   * Optimize for batch insertion.
   * 
   * See [[http://docs.neo4j.org/chunked/milestone/configuration-io-examples.html#configuration-batchinsert]]
   */
  def configParams = Map[String, String](
    "neostore.nodestore.db.mapped_memory" -> "90M",
    "neostore.relationshipstore.db.mapped_memory" -> "500M",
    "neostore.propertystore.db.mapped_memory" -> "50M",
    "neostore.propertystore.db.strings.mapped_memory" -> "100M",
    "neostore.propertystore.db.arrays.mapped_memory" -> "100M")

  def start(): Boolean = {
    log.info("Starting EmbeddedBatchWrapper")
    if (inserter != null) { log.warn("Wrapper already running"); return false }
    try {
      neoURI = new File(uri).toURI
    } catch { case e: URISyntaxException => log.error("Illegal URI syntax", e); return false }

    if (Files.notExists(Paths.get(neoURI))) { log.error("Directory does not exist at "+getNeoURI); return false }

    try {
      inserter = BatchInserters.inserter(getNeoURI)
      indexProvider = new LuceneBatchInserterIndexProvider(inserter)
      elems = indexProvider.nodeIndex(NodeProperties.labels.Concept.name, MapUtil.stringMap("type", "exact"))
      elems.setCacheCapacity(NodeProperties.Headword, 10000000)
      //documents.setCacheCapacity(nodeProperties("documentTitle"), 1000)
      uws = indexProvider.nodeIndex(NodeProperties.labels.UW.name, MapUtil.stringMap("type", "exact"))
      uws.setCacheCapacity(NodeProperties.Headword, 100000)
      // sents = indexProvider.nodeIndex(NodeProperties.labels.Sentence.name, MapUtil.stringMap("type", "exact"))
      flush()
      registerShutdownHook()
    } catch { case e: Exception => log.error("Failed to start EmbeddedBatchWrapper", e); return false }
    return true
  }

  def flush() = {
    elems.flush
    uws.flush
    log.info("Flushed batch indices")
  }

  def stop(): Boolean = {
    log.info("Stopping batch mode, and writing changes to disk...")
    try {
      var time = System.currentTimeMillis
      flush()
      indexProvider.shutdown()
      inserter.shutdown()
      log.info("Wrote batch to disk in "+(System.currentTimeMillis - time) / 1000.0+"s")
      return true
    } catch { case e: Exception => log.error("Failed to stop batch mode", e); return false }
  }

  def isConnected: Boolean = {
    /*  if (accessPoint.isEmpty) return false
    val result = NeoWrapper.query("START n=node(0) RETURN n")
    return result.size == 1
  */
    log.info("Connection works")
    true
  }
}