/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.wrappers

import java.net.{ ConnectException, URI }
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.scala.RestGraphDatabaseServiceProvider
import com.sun.jersey.api.client.{ Client, ClientResponse }
import com.sun.jersey.api.client.ClientHandlerException

@throws(classOf[java.net.ConnectException])
class RestNeoWrapper(override val uri: URI, override val userPw: Option[(String, String)] = None) extends RestGraphDatabaseServiceProvider with CDLNeoWrapper {

  var execEngine: ExecutionEngine = null

  def accessPoint = uri.toString

  def start = {
    registerShutdownHook
    if (execEngine == null) execEngine = new ExecutionEngine(ds.gds)
  }

  def stop = {
    if (isConnected) {
      ds.gds.shutdown
      execEngine = null
      logger.info("Stopped REST API wrapper")
    } else {
      logger.info("Tried to stop REST API wrapper that was not running")
    }
  }

  def isConnected: Boolean = {
    if (accessPoint.isEmpty) {
      logger.error("No valid REST access point")
      return false
    }
    val resource = Client.create.resource(accessPoint)
    val response = resource.get(classOf[ClientResponse])
    try {
      logger.info("GET on [%s], status code [%d]".format(accessPoint, response.getStatus))
    } catch {
      case ex: ClientHandlerException => logger.error("Not connected to "+accessPoint+": "+ex.getMessage); return false
    } finally response.close

    if (response.getStatus == 200) {
      logger.info("Connection to "+accessPoint+" works")
      return true
    } else {
      logger.error("Problem with connection to "+accessPoint)
      return false
    }

  }
}