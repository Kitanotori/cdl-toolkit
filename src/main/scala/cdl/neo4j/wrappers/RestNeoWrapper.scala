/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.neo4j.wrappers

import java.net.URI

import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.core.{ MediaType, Response }

class RestNeoWrapper(uri: String) extends CDLNeoWrapper {

  def start: Boolean = {
    if (neoURI == null) {
      try {
        neoURI = new URI(uri)
      } catch { case ex: Exception => log.error(ex.toString); return false }
      return isConnected
    }
    return false
  }

  def stop: Boolean = {
    neoURI = null
    return true
  }

  def isConnected: Boolean = {
    val request = ClientBuilder.newClient
      .target(neoURI)
      .path("/")
      .request(MediaType.APPLICATION_JSON_TYPE)
    var response: Response = null
    try {
      response = request.get
    } catch { case ex: Exception => log.error(ex.toString); return false }
    log.info("Connection to <" + neoURI.getPath + "> works")
    return response.getStatus == 200
  }
  /*
  private def isValidURL(url: String): Boolean = {
    try {
      val url = new URL(accessPoint)
      return true
    } catch { case ex: MalformedURLException => log.error(ex.toString); return false }
  }
  */
}