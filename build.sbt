lazy val gabbler = project
  .in(file("."))
  .enablePlugins(GitVersioning)
  .aggregate(`gabbler-chat`, `gabbler-user`)

lazy val `gabbler-user` = project
  .in(file("gabbler-user"))
  .enablePlugins(AutomateHeaderPlugin, JavaAppPackaging, DockerPlugin)

lazy val `gabbler-chat` = project
  .in(file("gabbler-chat"))
  .enablePlugins(AutomateHeaderPlugin, JavaAppPackaging, DockerPlugin)

name := "gabbler"

unmanagedSourceDirectories.in(Compile) := Vector.empty
unmanagedSourceDirectories.in(Test)    := Vector.empty

publishArtifact := false
