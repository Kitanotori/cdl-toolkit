package cdl.neo4j.wrappers

import org.neo4j.graphdb.DynamicLabel

/* Defines allowed node properties and labels */
object NodeProperties {
  val NodeType = "cdlType"
  val Headword = "hw"
  val Attributes = "attrs"
  val RealizationLabel = "rlabel"
  val UniversalWord = "uw"
  val AttributeName = "attr"
  val Constraints = "constraints"
  val DocumentTitle = "title"

  object labels {
    val UW = DynamicLabel.label("UW")
    val Concept = DynamicLabel.label("Concept")
    val Sentence = DynamicLabel.label("Sentence")
    val Document = DynamicLabel.label("Document")
  }
}
