/**
 * @author Petri Kivikangas
 * @date 6.2.2012
 *
 */
package cdl.neo4j.wrappers

import scala.language.implicitConversions

import org.neo4j.graphdb.RelationshipType

class UnsupportedRelType(reason: String) extends Exception(reason)

/**
 * Represents all available Universal Relations
 *
 * 	@throws(classOf[UnsupportedRelType])
 */
object RelType extends Enumeration with RelationshipType {
  type RelType = Value

  /* All possible relation types between nodes */
  val CONTAINS, ONTOLOGY, HYPO, agt, and, aoj, bas, ben, cag, cao, cnt, cob, con, coo, dur, equ, fmt, frm, gol, icl, ins, int, iof, man, met, mod, nam, obj, opl, or, per, plc, plf, plt, pof, pos, ptn, pur, qua, rsn, scn, seq, shd, src, tim, tmf, tmt, to, via = Value

  implicit def conv(rt: RelType) = new RelationshipType() { def name = rt.toString }

  def toRelType(rel: String): RelType = this.Value(rel)

  def getTypes: Array[String] = {
    var x = List[String]()
    values.foreach(v => {
      if (v.name.head.isLower) { // filter out non-standard, i.e. uppercase, relations
        x ::= v.name
      }
    })
    return x.reverse.toArray
  }
}