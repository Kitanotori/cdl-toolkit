/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.wrappers

import org.neo4j.cypher.ExecutionEngine
import org.neo4j.kernel.EmbeddedGraphDatabase
import org.neo4j.scala.{ DatabaseService, EmbeddedGraphDatabaseServiceProvider, Neo4jIndexProvider }
import org.neo4j.server.WrappingNeoServerBootstrapper

import com.sun.jersey.api.client.{ Client, ClientResponse }

/* 
 * Extending EmbeddedGraphDatabaseServiceProvider creates an instance of EmbeddedGraphDatabase,
 * which starts the server by calling InternalAbstractGraphDatabase#run()
 * 
 * TODO: DOES NOT WORK CURRENTLY. Use EmbeddedBatchInserter or RestNeoWrapper instead.
 * 
 */
class EmbeddedNeoWrapper(storeDir: String) extends CDLNeoWrapper with EmbeddedGraphDatabaseServiceProvider with Neo4jIndexProvider {
  var execEngine: ExecutionEngine = null
  var uri = ""
  var srv: WrappingNeoServerBootstrapper = null

  def accessPoint: String = uri
  def neo4jStoreDir: String = storeDir

  def start() = {
    if (srv == null) {
      registerShutdownHook

      /* 
       * Initialize indexes
       * 
       * Default configuration: MapUtil.stringMap( IndexManager.PROVIDER, "lucene", "type", "fulltext" ) )
       * More info: http://docs.neo4j.org/chunked/stable/indexing-create-advanced.html
       * 
       */
      execEngine = new ExecutionEngine(ds.gds)
      srv = new WrappingNeoServerBootstrapper(ds.gds.asInstanceOf[EmbeddedGraphDatabase])
      srv.start
      uri = srv.getServer.baseUri.toString
      logger.info("Started server at "+uri)
    }
  }

  def stop() = {
    if (srv != null) {
      srv.stop
      srv = null
      logger.info("Stopped server")
      ds.gds.shutdown
      logger.info("Disconnected from database")
    }
  }

  def isConnected: Boolean = {
    if (accessPoint.isEmpty) return false
    val resource = Client.create.resource(accessPoint)
    val response = resource.get(classOf[ClientResponse])
    logger.info("GET on [%s], status code [%d]".format(accessPoint, response.getStatus))
    response.close
    return response.getStatus == 200
  }
}