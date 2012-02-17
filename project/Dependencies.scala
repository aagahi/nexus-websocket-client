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
  }

  object Compile {
  }

  object Test {
    val netty         = "io.netty"        % "netty"               % V.netty   % "test"
  }

  object Runtime {
  }


}
