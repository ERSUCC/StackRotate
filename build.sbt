name := "Stack Rotate"
version := "0.1.0"
organization := "org.ersucc"

scalaVersion := "3.7.0"

scalacOptions ++= Seq("-Werror", "-Wunused:linted")

resolvers += "scijava" at "https://maven.scijava.org/content/repositories/releases/"

libraryDependencies ++= Seq(
  "net.imagej" % "ij" % "1.54p",
  "sc.fiji" % "Auto_Local_Threshold" % "1.11.0"
)
