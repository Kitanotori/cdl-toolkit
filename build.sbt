name := "CDL Toolkit"

version := "0.8"

scalaVersion := "2.9.2"

mainClass in (Compile, run) := Some("cdl.editor.Editor")

scalacOptions ++= Seq("-unchecked", "-deprecation", "–encoding UTF8", "-target:jvm-1.5")
 
resolvers ++= Seq(
	"fakod-snapshots" at "https://raw.github.com/FaKod/fakod-mvn-repo/master/snapshots",
	"fakod-releases" at "https://raw.github.com/FaKod/fakod-mvn-repo/master/releases",
	"neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases",
	"spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= Seq(
	"org.codehaus.janino" % "janino" % "2.6.1",
	/* "io.spray" % "spray-client" % "1.1-M4.2",
	"org.springframework.data" % "spring-data-neo4j" % "2.1.0.RELEASE" withSources,
	"com.typesafe.akka" % "akka-actor" % "2.0.3" withSources, */
	"org.scalatest" % "scalatest_2.9.2" % "2.0.M4" withSources, 
	"junit" % "junit" % "4.10",
	"commons-io" % "commons-io" % "2.4",
	"ch.qos.logback" % "logback-core" % "1.0.7",
	"ch.qos.logback" % "logback-classic" % "1.0.7",
	"org.slf4j" % "slf4j-api" % "1.6.6",
	"org.neo4j" % "neo4j-scala" % "0.2.0-M2-SNAPSHOT" withSources,
	"org.neo4j" % "neo4j" % "1.8" withSources,
	"org.neo4j" % "neo4j-cypher" % "1.8",
	/** Deps for Embedding the Neo4j Admin server - works around: http://neo4j.org/forums/#nabble-td3477583 */
	"org.neo4j.app" % "neo4j-server" % "1.8" withSources,
	"org.neo4j.app" % "neo4j-server" % "1.8" classifier "static-web" from 
	"http://m2.neo4j.org/releases/org/neo4j/app/neo4j-server/1.8/neo4j-server-1.8-static-web.jar",
	"com.sun.jersey" % "jersey-client" % "1.4" withSources
)
