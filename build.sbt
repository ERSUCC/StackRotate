import java.nio.file.{ Files, Paths }

import sbt.{ InputKey, IO }
import sbt.complete.DefaultParsers.{ Space, StringBasic }

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

val packageAndCopy = InputKey[Unit]("packageAndCopy", "Package and copy the compiled jar to the specified directory")

packageAndCopy := {
  val path = Paths.get((Space ~> StringBasic).parsed)

  if (!Files.exists(path))
    throw new Exception("The specified path does not exist.")

  if (!Files.isDirectory(path))
    throw new Exception("The specified path is not a directory.")

  val jar = (Compile / packageBin).value

  IO.copyFile(jar, path.resolve(jar.getName).toFile)
}
