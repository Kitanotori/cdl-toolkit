/**
 * @author Petri Kivikangas
 * @date 8.11.2012
 *
 */
package cdl.editor

import java.io.{ FileInputStream, FileNotFoundException, IOException, InputStream }
import java.nio.file.FileSystems
import java.util.Properties
import scala.collection.JavaConversions.asScalaSet
import org.slf4j.LoggerFactory
import java.io.File

object Config {
  private val logger = LoggerFactory.getLogger(this.getClass)
  val configFile = new Properties

  def getProperty(key: String): Option[String] = {
    configFile.getProperty(key) match {
      case null      => logger.error("Could not find property '"+key+"' from configuration file"); None
      case p: String => Some(p)
    }
  }

  def readConfig(confFile: File) = {
    try {
      val is = new FileInputStream(confFile)
      configFile.load(is)
      //configFile.entrySet.map(e => println(e.getKey+": "+(e.getValue.toString)))
      is.close
      logger.info("Loaded configurations from "+confFile.getAbsolutePath+":")
    } catch {
      case ex: FileNotFoundException => {
        logger.info("Config file "+confFile.getAbsolutePath+" not found")
      }
      case ex: IOException => {
        logger.error(
          "Unable to access configuration file"+confFile.getAbsolutePath, ex)
      }
    }
  }
}