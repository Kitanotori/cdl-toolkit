/**
 * @author Petri Kivikangas
 * @date 23.4.2012
 *
 */
package cdl.wrappers

import org.neo4j.cypher.ExecutionEngine
import org.neo4j.scala.{ BatchGraphDatabaseServiceProvider, Neo4jBatchIndexProvider }

import cdl.wrappers.NeoWrapper.nodeProperties

class EmbeddedBatchInserter(storeDir: String) extends CDLNeoWrapper with Neo4jBatchIndexProvider with BatchGraphDatabaseServiceProvider {
  private var isInBatch: Boolean = false

  var execEngine: ExecutionEngine = null

  override def neo4jStoreDir = storeDir

  def accessPoint = neo4jStoreDir

  /* 
   * Optimize for batch insertion.
   * 
   * See [[http://docs.neo4j.org/chunked/milestone/configuration-io-examples.html#configuration-batchinsert]]
   *
  override def configParams = Map[String, String](
    "neostore.nodestore.db.mapped_memory" -> "90M",
    "neostore.relationshipstore.db.mapped_memory" -> "500M",
    "neostore.propertystore.db.mapped_memory" -> "50M",
    "neostore.propertystore.db.strings.mapped_memory" -> "100M",
    "neostore.propertystore.db.arrays.mapped_memory" -> "100M")
    */

  def start() = {
    logger.info("Starting batch mode")
    concepts.setCacheCapacity(nodeProperties("headword"), 10000000)
    documents.setCacheCapacity(nodeProperties("documentTitle"), 1000)
    uws.setCacheCapacity(nodeProperties("universalWord"), 100000)
    flush
    registerShutdownHook
  }

  def flush() = {
    concepts.flush
    documents.flush
    uws.flush
  }

  def stop() = {
    logger.info("Stopping batch mode, and writing changes to disk")
    var time = System.currentTimeMillis
    flush
    shutdownIndex
    shutdown(ds)
    logger.info("Wrote batch to disk in "+(System.currentTimeMillis - time) / 1000.0+"s")
  }

  def isConnected: Boolean = {
    if (accessPoint.isEmpty) return false
    val result = NeoWrapper.query("START n=node(0) RETURN n")
    return result.size == 1
  }
}