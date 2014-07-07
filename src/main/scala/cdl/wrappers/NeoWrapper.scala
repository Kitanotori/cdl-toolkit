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
import org.apache.lucene.search.WildcardQuery
import org.neo4j.cypher.{ ExecutionEngine, ExecutionResult, SyntaxException }
import org.neo4j.graphdb.{ Node, PropertyContainer }
import org.neo4j.graphdb.index.{ Index, IndexManager }
import org.neo4j.tooling.GlobalGraphOperations
import org.slf4j.LoggerFactory
import cdl.editor.Config
import cdl.objects.{ CDLDocument, Concept, Statement, UW }
import cdl.parser.{ CDLParser, OntologyParser }
import cdl.parser.OntologyParser.UwNode
import cdl.wrappers.RelTypes.{ CONTAINS, HYPO, ONTOLOGY, conv }
import org.neo4j.graphdb.GraphDatabaseService

class NeoWrapperException(reason: String) extends Exception(reason)

trait CDLNeoWrapper {

  protected val logger = LoggerFactory.getLogger(this.getClass)

  /* Implement these */
  def accessPoint: String
  def start()
  def stop()
  def isConnected: Boolean
  def getHyponyms(uw: UW): Iterator[String]
  def query(q: String): ExecutionResult
  def cleanDB()
  def fetchUWs(hw: String): Iterator[UW]
  def importDocuments(docs: Array[File])
  def importOntology()

  protected def registerShutdownHook() = {
    // Registers a shutdown hook for the Neo4j instance so that it
    // shuts down nicely when the VM exits (even if you "Ctrl-C" the
    // running example before it's completed)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run = NeoWrapper.stop
    })
  }
}

object NeoWrapper {
  protected val logger = LoggerFactory.getLogger(this.getClass)
  var impl: CDLNeoWrapper = null

  /* Direct the db calls to wrapper implementations */
  def getHyponyms(uw: UW): Iterator[String] = impl.getHyponyms(uw)
  def query(q: String): ExecutionResult = impl.query(q)
  def cleanDB() = impl.cleanDB
  def fetchUWs(head: String): Iterator[UW] = impl.fetchUWs(head)
  def importDocuments(docs: Array[File]) = impl.importDocuments(docs)
  def isConnected: Boolean = impl.isConnected
  def stop() = impl.stop
  def start() = impl.start
  def importOntology() = impl.importOntology

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
          case ex => logger.error("Could not create RestNeoWrapper for "+uri+": "+ex.getMessage); impl = null
        }
      case None => logger.info("Unable to toggle RestNeoWrapper")
    }
  }

  def toggleRestBatchWrapper() = {
    // TODO: implement
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

}
