/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.unl

import java.io.File
import java.nio.file.FileSystems

import scala.io.Source
import scala.util.Sorting

import org.slf4j.LoggerFactory

/* 
 * The idea of this object is to provide the attributes specified by UNL/CDL specification.
 * 
 * See: {@linktourl http://www.undl.org/unlsys/unl/unl2005/attribute.htm}
 
object Attributes {
  private val logger = LoggerFactory.getLogger(this.getClass)
  def getAttributes: Array[String] = {
    var attrsFile: File = null
    try {
      attrsFile = FileSystems.getDefault.getPath("resources/attributes.txt").toFile
    } catch {
      case ex: Exception => logger.error("Couldn't read attributes.txt")
    }

    val attrsArr = Source.fromFile(attrsFile).getLines.toArray
    Sorting.quickSort(attrsArr)
    RelTypes.getTypes
    attrsArr
  }
}*/