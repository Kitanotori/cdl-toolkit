/**
 * @author Petri Kivikangas
 * @date 9.11.2012
 *
 */
package cdl.unl

import java.nio.file.FileSystems
import cdl.wrappers.RelTypes
import java.io.File
import org.slf4j.LoggerFactory
import scala.util.Sorting
import scala.io.Source

/* 
 * This object will provide Universal Words.
 * 
 * See: {@linktourl http://www.undl.org/unlsys/uw/uwmanv20.htm}
 * See: {@linktourl http://www.undl.org/unlsys/uw/UNLOntology.html}
 */
object UWs {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val uwSource = "resources/UNLOntology_tree.txt"

  /* 
   * Fetches UWs from Neo4j. One has to make sure that UWs 
   * are imported to database before calling this method.
   * 
   */
  def getUWsFromNeo: Array[String] = {
    var ontology: File = null
    try {
      ontology = FileSystems.getDefault.getPath(uwSource).toFile
    } catch {
      case ex: Exception => logger.error("Couldn't read "+uwSource)
    }

    val attrsArr = Source.fromFile(ontology).getLines.toArray
    Sorting.quickSort(attrsArr)
    RelTypes.getTypes
    attrsArr
  }
}