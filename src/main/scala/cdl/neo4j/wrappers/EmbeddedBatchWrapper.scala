/**
 * @author Petri Kivikangas
 * @date 23.4.2012
 *
 */
package cdl.neo4j.wrappers

import java.io.File
import java.net.URI

import org.neo4j.cypher.ExecutionResult
import org.neo4j.helpers.collection.MapUtil
import org.neo4j.index.lucene.unsafe.batchinsert.LuceneBatchInserterIndexProvider
import org.neo4j.unsafe.batchinsert.{BatchInserter, BatchInserterIndex, BatchInserterIndexProvider, BatchInserters}

import cdl.objects.UW

class EmbeddedBatchWrapper(uri: String) extends CDLNeoWrapper {
  private var isInBatch: Boolean = false

  private var inserter: BatchInserter = null
  private var indexProvider: BatchInserterIndexProvider = null
  private var uws: BatchInserterIndex = null
  private var elems: BatchInserterIndex = null
  private var sents: BatchInserterIndex = null

  def getHyponyms(uw: UW): Iterator[String]
  def query(q: String): ExecutionResult
  def cleanDB()
  def fetchUWs(hw: String): Iterator[UW]

  def importDocuments(docs: Array[File]) {

  }

  def importOntology() {

  }

  /* 
   * Optimize for batch insertion.
   * 
   * See [[http://docs.neo4j.org/chunked/milestone/configuration-io-examples.html#configuration-batchinsert]]
   */
  override def configParams = Map[String, String](
    "neostore.nodestore.db.mapped_memory" -> "90M",
    "neostore.relationshipstore.db.mapped_memory" -> "500M",
    "neostore.propertystore.db.mapped_memory" -> "50M",
    "neostore.propertystore.db.strings.mapped_memory" -> "100M",
    "neostore.propertystore.db.arrays.mapped_memory" -> "100M")

  def start(): Boolean = {
    log.info("Starting EmbeddedBatchWrapper")
    try {
      neoURI = new URI(uri)
      inserter = BatchInserters.inserter(getNeoURI)
      indexProvider = new LuceneBatchInserterIndexProvider(inserter)
      elems = indexProvider.nodeIndex(NodeProperties.labels.Concept.name, MapUtil.stringMap("type", "exact"))
      elems.setCacheCapacity(NodeProperties.Headword, 10000000)
      //documents.setCacheCapacity(nodeProperties("documentTitle"), 1000)
      uws = indexProvider.nodeIndex(NodeProperties.labels.UW.name, MapUtil.stringMap("type", "exact"))
      uws.setCacheCapacity(NodeProperties.Headword, 100000)
      flush()
      registerShutdownHook()
    } catch { case e: Exception => log.error(e.toString, "Failed to start EmbeddedBatchWrapper"); return false }
    return true
  }

  def flush() = {
    sents.flush
    uws.flush
  }

  def stop() = {
    log.info("Stopping batch mode, and writing changes to disk...")
    var time = System.currentTimeMillis
    flush()
    indexProvider.shutdown()
    inserter.shutdown()
    log.info("Wrote batch to disk in " + (System.currentTimeMillis - time) / 1000.0 + "s")
  }

  def isConnected: Boolean = {
    /*  if (accessPoint.isEmpty) return false
    val result = NeoWrapper.query("START n=node(0) RETURN n")
    return result.size == 1
  */
    true
  }
}