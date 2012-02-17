import sbt._
import Keys._


object ProjectBuild extends Build {

  import Dependencies._

  lazy val basicSettings = Seq[Setting[_]](
    organization := "ws.nexus",
    name := "websocket-client",
    version := "0.1",
    description := "Nexus WS client",
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    scalaVersion := "2.9.1",
    crossScalaVersions := Seq("2.8.2", "2.9.1"),
    resolvers ++= Dependencies.resolutionRepos
  )


  lazy val root = Project("websocket-client", file("."))
    .settings(basicSettings: _*)
    .settings(
    libraryDependencies ++= Seq(
      Test.netty
    ) )
    .settings(
      libraryDependencies <<= (scalaVersion, libraryDependencies) { (sv, deps) =>
        // select the Specs2 version based on the Scala version
        val versionMap = Map("2.9.1" -> "1.7.1", "2.8.2" -> "1.5" )
        val testVersion = versionMap( sv )
        deps :+ ( "org.specs2"      %% "specs2"             % testVersion  % "test")
      }
    )


}
