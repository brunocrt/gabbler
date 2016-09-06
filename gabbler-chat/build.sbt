libraryDependencies ++= Vector(
  Library.akkaClusterSharding,
  Library.akkaHttp,
  Library.akkaHttpCirce,
  Library.akkaLog4j,
  Library.akkaPersistenceCassandra,
  Library.akkaSse,
  Library.circeGeneric,
  Library.constructrAkka,
  Library.constructrCoordinationEtcd,
  Library.log4jCore,
  Library.log4jSlf4jImpl,
  Library.akkaHttpTestkit         % "test",
  Library.akkaPersistenceInmemory % "test",
  Library.akkaTestkit             % "test",
  Library.scalaTest               % "test"
)

initialCommands := """|import de.heikoseeberger.gabbler.chat._
                      |""".stripMargin

daemonUser.in(Docker) := "root"
maintainer.in(Docker) := "Heiko Seeberger"
version.in(Docker)    := "latest"
dockerBaseImage       := "java:8"
dockerExposedPorts    := Vector(2552, 8000)
dockerRepository      := Some("hseeberger")
