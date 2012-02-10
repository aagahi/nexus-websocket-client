import sbt._

object Dependencies {

  val resolutionRepos = Seq(
    "Scalatools Snapshots"    at "http://scala-tools.org/repo-snapshots",
    "Maven central"           at "http://repo1.maven.org/maven2",
    "Local Maven Repository"  at "file:///Users/alag/.m2/repository/",
    ScalaToolsSnapshots
  )

  object V {
    val netty       = "3.3.1.Final"
    val slf4j       = "1.6.1"
    val logback     = "0.9.29"
  }

  object Compile {
  }

  object Test {
    val netty         = "io.netty"        % "netty"               % V.netty   % "test"
    val slf4j         = "org.slf4j"       % "slf4j-api"           % V.slf4j   % "test"
    val logback       = "ch.qos.logback"  % "logback-classic"     % V.logback % "test"
    val junit         = "junit"           % "junit"               % "4.7"     % "test"

  }

  object Runtime {
    val slf4j     = "org.slf4j"                       % "slf4j-api"             % V.slf4j
    val logback   = "ch.qos.logback"                  % "logback-classic"       % V.logback
  }


}
