lazy val gabbler = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin, GitVersioning)

libraryDependencies ++= Vector(
  Library.scalaTest % "test"
)

initialCommands := """|import de.heikoseeberger.gabbler._
                      |""".stripMargin
