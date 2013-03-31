/**
 * @author Petri Kivikangas
 * @date 1.2.2011
 *
 */
package cdl.query

import scala.collection.mutable.StringBuilder

import org.neo4j.cypher.ExecutionResult
import org.slf4j.LoggerFactory

import cdl.objects.{ Arc, Concept, DefinitionLabel, Statement }
import cdl.wrappers.NeoWrapper
import cdl.parser.CDLParser

object CDLQuery {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def it = this // convenience method for Java

  def getQuery(cdlQuery: String): CDLQuery = {
    val parsedStatement = CDLParser.parseDocument(cdlQuery).entities(0).asInstanceOf[Statement]
    var qvars = List[Concept]()
    var concepts = List[Concept]()
    parsedStatement.entities.foreach(entity => {
      if (entity.asInstanceOf[Concept].uw.startsWith("?")) {
        qvars ::= entity.asInstanceOf[Concept]
      } else {
        concepts ::= entity.asInstanceOf[Concept]
      }
    })
    return new CDLQuery(concepts.reverse, parsedStatement.arcs, qvars.reverse)
  }

  def getCypher(cdlQuery: String, expansion: Int): String = {
    return getQuery(cdlQuery).toCypher(expansion)
  }

  /**
   * Converts CDL query into Cypher query.
   *
   * TODO: allow use of attributes and inner entities
   *
   * @param cdlQuery
   * @return Cypher query string
   */
  def getCypher(cdlQuery: CDLQuery, expansion: Int = 0): String = {
    if (cdlQuery.entities.isEmpty) return ""
    var cypherQuery = new StringBuilder("START\tx"+cdlQuery.entities(0).rlabel+"=node:concepts(uw='"+cdlQuery.entities.head.uw+"')")
    if (cdlQuery.arcs.nonEmpty) {
      cypherQuery ++= "\nMATCH"
      cdlQuery.arcs.map(arc => "\t"+arc.toCypherString).addString(cypherQuery, ",\n")
    }
    cypherQuery ++= "\nWHERE"
    cdlQuery.entities.map(entity => entity match {
      case s: Statement => "" // TODO: allow use of inner entities in the query
      case c: Concept => {
        /* The level of query expansion */
        expansion match {
          /* Do exact concept matching */
          case 0 => "\tx"+c.rlabel+".uw! = '"+c.uw+"'"
          /* Expand query with hyponyms */
          case 1 => {
            val hyponyms = NeoWrapper.getHyponyms(c)
            if (hyponyms.isEmpty) {
              logger.info("Didn't find hyponyms for ["+c.uw + ']')
              "\tx"+c.rlabel+".uw! = '"+c.uw+"'"
            } else {
              val sb = new StringBuilder("\t(x"+c.rlabel+".uw! = '"+c.uw+"' OR ")
              hyponyms.map(h => "x"+c.rlabel+".uw! = '"+h+"'").addString(sb, " OR ")
              sb += ')'
              sb.toString
            }
          }
          /* TODO: utilize also other semantic relations in the query */
        }
      }
    }).addString(cypherQuery, " AND\n")

    if (cdlQuery.queryVars.nonEmpty) {
      cypherQuery ++= "\nRETURN"
      cdlQuery.queryVars.map(qvar => "\tx"+qvar.rlabel).addString(cypherQuery, ",\n")
    } else {
      cypherQuery ++= "\nRETURN"
      cdlQuery.entities.map(e => "\tx"+e.rlabel).addString(cypherQuery, ",\n")
    }
    return cypherQuery.toString
  }
}

class CDLQuery(override val entities: List[Concept], override val arcs: List[Arc], val queryVars: List[Concept])
  extends Statement("", DefinitionLabel.Null, entities ::: queryVars, arcs) {

  def toCypher(expansion: Int): String = CDLQuery.getCypher(this, expansion)

  def execute: ExecutionResult = {
    val result = NeoWrapper.query(toCypher(0))
    CDLQuery.logger.info("Query returned: "+result)
    return result
  }

  /* TODO: implement maybe
  def resultToCDL(result: ExecutionResult): Concept = {
    val res = result.javaIterator
    var cons = List[Concept]()
    while(res.hasNext) {
      val c = res.next
      val rlabel = c.get(Neo4jCDLWrapper.nodeProperties("rlabel")).asInstanceOf[String]
      val uw = c.get(Neo4jCDLWrapper.nodeProperties("uw")).asInstanceOf[String]
      cons ::= new Concept(rlabel, uw)
    }
    cons(0)
  }*/
}