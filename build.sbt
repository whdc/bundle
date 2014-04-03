import AssemblyKeys._

name := "linkage"

version := "0.1"

organization := "edu.berkeley.linguistics"

scalaVersion := "2.10.4"

libraryDependencies  ++= Seq(
  "net.sf.opencsv" % "opencsv" % "2.0",
  // "org.scalanlp" % "breeze_2.10" % "0.7"
  "org.scalanlp" % "breeze-natives_2.10" % "0.7"
)

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

lazy val buildSettings = Seq(
  version := "0.1",
  organization := "edu.berkeley.linguistics",
  scalaVersion := "2.10.4"
)

val app = (project in file("app")).
  settings(buildSettings: _*).
  settings(assemblySettings: _*)



mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
    case "application.conf"                            => MergeStrategy.concat
    case "META-INF/MANIFEST.MF"                        => MergeStrategy.discard
    case x => MergeStrategy.first
    // case x => old(x)
  }
}

net.virtualvoid.sbt.graph.Plugin.graphSettings

