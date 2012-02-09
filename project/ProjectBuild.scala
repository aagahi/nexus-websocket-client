import sbt._
import Keys._


object ProjectBuild extends Build {

  import Dependencies._

  lazy val basicSettings = Seq[Setting[_]](
    organization := "ws.nexus",
    name := "websocket-client",
    version := "0.1",
    description := "Nexus WS client",
    scalaVersion := "2.9.1",
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    resolvers ++= Dependencies.resolutionRepos
  )


  lazy val root = Project("pubsub-service", file("."))
    .settings(basicSettings: _*)
    .settings(
    libraryDependencies ++= Seq(
      Compile.netty,
      Test.specs2,
      Test.specs2Scalaz,
      Test.junit,
      Runtime.logback
    )
  )

}
