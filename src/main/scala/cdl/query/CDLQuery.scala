/**
 * @author Petri Kivikangas
 * @date 1.2.2011
 *
 */
package cdl.query

import scala.collection.mutable.StringBuilder
import scala.collection.JavaConversions._
import org.neo4j.cypher.ExecutionResult
import org.slf4j.LoggerFactory
import cdl.neo4j.wrappers.NeoWrapper
import cdl.objects.{ Attribute, ComplexEntity, DefinitionLabel, ElementalEntity, Entity, RealizationLabel, Relation, UW }
import cdl.parser.CDLParser
import cdl.objects.ElementalRelation

object CDLQuery {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def it = this // convenience method for Java

  def getQuery(cdlQuery: String): CDLQuery = {
    val parsedStatement = CDLParser.parseDocument(cdlQuery).entities.head.asInstanceOf[ComplexEntity]
    var qvars = List[ElementalEntity]()
    var concepts = List[ElementalEntity]()
    parsedStatement.entities.foreach(entity => {
      if (entity.asInstanceOf[ElementalEntity].rlabel.toString.startsWith("?")) {
        qvars ::= entity.asInstanceOf[ElementalEntity]
      } else {
        concepts ::= entity.asInstanceOf[ElementalEntity]
      }
    })
    return new CDLQuery(concepts.reverse, parsedStatement.relations, qvars.reverse)
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
    var cypherQuery = new StringBuilder("START\tx"+cdlQuery.entities.head.rlabel+"=node:concepts(uw='"+cdlQuery.entities.head.dlabel+"')")
    if (cdlQuery.relations.nonEmpty) {
      cypherQuery ++= "\nMATCH"
      cdlQuery.relations.map(arc => "\t"+arc.toCypherString).addString(cypherQuery, ",\n")
    }
    cypherQuery ++= "\nWHERE"
    cdlQuery.entities.map(entity => entity match {
      case e: ComplexEntity => "" // TODO: allow use of inner entities in the query
      case e: ElementalEntity => {
        /* The level of query expansion */
        expansion match {
          /* Do exact concept matching */
          case 0 => "\tx"+e.rlabel+".uw! = '"+e.dlabel+"'"
          /* Expand query with hyponyms */
          case 1 => {
            val hyponyms = NeoWrapper.getHyponyms(e.asInstanceOf[UW])
            if (hyponyms.isEmpty) {
              logger.info("Didn't find hyponyms for ["+e.dlabel + ']')
              "\tx"+e.rlabel+".uw! = '"+e.dlabel+"'"
            } else {
              val sb = new StringBuilder("\t(x"+e.rlabel+".uw! = '"+e.dlabel+"' OR ")
              hyponyms.map(h => "x"+e.rlabel+".uw! = '"+h+"'").addString(sb, " OR ")
              sb += ')'
              sb.toString
            }
          }
          /* TODO: utilize also other semantic relations in the query */
        }
      }
    }).addString(cypherQuery, " AND\n")

    if (cdlQuery.qvars.nonEmpty) {
      cypherQuery ++= "\nRETURN"
      cdlQuery.qvars.map(qvar => "\tx"+qvar.rlabel).addString(cypherQuery, ",\n")
    } else {
      cypherQuery ++= "\nRETURN"
      cdlQuery.entities.map(e => "\tx"+e.rlabel).addString(cypherQuery, ",\n")
    }
    return cypherQuery.toString
  }
}

class CDLQuery(
  ent: Iterable[Entity] = Nil,
  rel: Iterable[Relation] = Nil,
  val qvars: Iterable[Entity] = Nil,
  rl: RealizationLabel = new RealizationLabel(),
  dl: DefinitionLabel = new DefinitionLabel(),
  atr: Iterable[Attribute] = Nil)
  extends ComplexEntity(rl, dl, atr, ent ++ qvars, rel) {

  def this(ents: java.util.Enumeration[UW], rels: java.util.Enumeration[ElementalRelation]) = this(ents.toList, rels.toList, Nil, new RealizationLabel(), new DefinitionLabel(), Nil)

  def toCypher(expansion: Int): String = CDLQuery.getCypher(this, expansion)

  def execute: ExecutionResult = {
    val result = NeoWrapper.query(toCypher(0))
    CDLQuery.logger.info("Query returned: "+result)
    return result
  }

  /* TODO: implement maybe
  def resultToCDL(result: ExecutionResult): Concept = {
    val res = result.javaIterator
    var cons = Iterable[Concept]()
    while(res.hasNext) {
      val c = res.next
      val rlabel = c.get(Neo4jCDLWrapper.nodeProperties("rlabel")).asInstanceOf[String]
      val uw = c.get(Neo4jCDLWrapper.nodeProperties("uw")).asInstanceOf[String]
      cons ::= new Concept(rlabel, uw)
    }
    cons(0)
  }*/
}