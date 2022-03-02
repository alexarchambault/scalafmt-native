import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.1`
import $ivy.`io.github.alexarchambault.mill::mill-native-image_mill0.9:0.1.12`
import $ivy.`io.github.alexarchambault.mill::mill-native-image-upload:0.1.12`

import de.tobiasroeser.mill.vcs.version._
import io.github.alexarchambault.millnativeimage.NativeImage
import io.github.alexarchambault.millnativeimage.upload.Upload
import mill._
import mill.scalalib._

def scalafmtVersion = "3.4.1"

trait ScalafmtNativeImage extends ScalaModule with NativeImage {
  def scalaVersion = "2.13.6"

  def nativeImageClassPath = T{
    runClasspath()
  }
  def nativeImagePersist = System.getenv("CI") != null
  def nativeImageGraalVmJvmId = "graalvm-java11:21.2.0"
  def nativeImageName = "scalafmt"
  def ivyDeps = super.ivyDeps() ++ Seq(
    ivy"org.scalameta::scalafmt-cli:$scalafmtVersion"
  )
  def nativeImageMainClass = "org.scalafmt.cli.Cli"

  def nameSuffix = ""
  def copyToArtifacts(directory: String = "artifacts/") = T.command {
    val _ = Upload.copyLauncher(
      nativeImage().path,
      directory,
      "scalafmt",
      compress = true,
      suffix = nameSuffix
    )
  }
}

object native extends ScalafmtNativeImage

def csDockerVersion = "2.0.16"

object `native-static` extends ScalafmtNativeImage {
  def nameSuffix = "-static"
  def nativeImageDockerParams = Some(
    NativeImage.linuxStaticParams(
      "messense/rust-musl-cross@sha256:12d0dd535ef7364bf49cb2608ae7eaf60e40d07834eb4d9160c592422a08d3b3",
      s"https://github.com/coursier/coursier/releases/download/v$csDockerVersion/cs-x86_64-pc-linux"
    )
  )
}

object `native-mostly-static` extends ScalafmtNativeImage {
  def nameSuffix = "-mostly-static"
  def nativeImageDockerParams = Some(
    NativeImage.linuxMostlyStaticParams(
      "ubuntu:18.04", // TODO Pin that?
      s"https://github.com/coursier/coursier/releases/download/v$csDockerVersion/cs-x86_64-pc-linux"
    )
  )
}


def publishVersion = T{
  val state = VcsVersion.vcsState()
  if (state.commitsSinceLastTag > 0) {
    val versionOrEmpty = state.lastTag
      .filter(_ != "latest")
      .map(_.stripPrefix("v"))
      .flatMap { tag =>
        val idx = tag.lastIndexOf(".")
        if (idx >= 0) Some(tag.take(idx + 1) + (tag.drop(idx + 1).toInt + 1).toString + "-SNAPSHOT")
        else None
      }
      .getOrElse("0.0.1-SNAPSHOT")
    Some(versionOrEmpty)
      .filter(_.nonEmpty)
      .getOrElse(state.format())
  } else
    state
      .lastTag
      .getOrElse(state.format())
      .stripPrefix("v")
}

def upload(directory: String = "artifacts/") = T.command {
  val version = publishVersion()

  val path = os.Path(directory, os.pwd)
  val launchers = os.list(path).filter(os.isFile(_)).map { path =>
    path.toNIO -> path.last
  }
  val ghToken = Option(System.getenv("UPLOAD_GH_TOKEN")).getOrElse {
    sys.error("UPLOAD_GH_TOKEN not set")
  }
  val (tag, overwriteAssets) =
    if (version.endsWith("-SNAPSHOT")) ("launchers", true)
    else ("v" + version, false)

  Upload.upload("scala-cli", "scalafmt-native-image", ghToken, tag, dryRun = false, overwrite = overwriteAssets)(launchers: _*)
}
