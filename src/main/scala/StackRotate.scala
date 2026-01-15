package org.ersucc.stackrotate

import fiji.threshold.Auto_Local_Threshold

import ij.{ IJ, ImagePlus, WindowManager }
import ij.gui.GenericDialog
import ij.plugin.PlugIn
import ij.process.ImageProcessor

import java.awt.Color
import java.io.PrintWriter
import java.nio.file.{ Files, Paths }

import scala.math.{ atan, toDegrees }

class StackRotate extends PlugIn {
  override def run(arg: String): Unit = {
    if (WindowManager.getImageCount() == 0)
      return IJ.error("Stack Rotate", "No images open.")

    val dialog = new GenericDialog("Stack Rotate")

    Option(WindowManager.getActiveWindow).foreach(window => dialog.setIconImages(window.getIconImages()))

    dialog.addChoice("Select reference image:", "--" +: WindowManager.getImageTitles(), null)
    dialog.addFileField("Import transformations from file:", null)
    dialog.addImageChoice("Select image/stack to align:", null)
    dialog.addNumericField("Minimum angle to rotate:", 0)
    dialog.addDirectoryField("Save transformations to directory:", null)
    dialog.showDialog()

    if (dialog.wasOKed) {
      val refTitle = dialog.getNextChoice()
      val importRef = Option(dialog.getNextString()).filter(_.trim.nonEmpty).map(Paths.get(_))
      val stackImage = dialog.getNextImage()
      val minAngle = dialog.getNextNumber()

      importRef match {
        case Some(refPath) =>
          if (!Files.exists(refPath))
            return IJ.error("Stack Rotate", "The specified input path does not exist.")

          if (!Files.isRegularFile(refPath))
            return IJ.error("Stack Rotate", "The specified input path is not a file.")

          val refContents = Files.readAllLines(refPath).toArray(Array[String]()).flatMap(_.toDoubleOption)

          val stack = stackImage.getImageStack()
          val slices = stack.size()

          if (refContents.size != slices)
            return IJ.error("Stack Rotate", "The specified transformation file has a different number of slices than the selected stack.")

          IJ.showProgress(0, slices)

          for (i <- 1 to slices) {
            stack.getProcessor(i).rotate(refContents(i - 1))

            IJ.showProgress(i, slices)
          }

        case _ =>
          if (refTitle == "--")
            return IJ.error("Stack Rotate", "You must specify either a reference image or a transformation file.")

          val refImage = WindowManager.getImage(refTitle)
          val refType = refImage.getType()

          if (refType == ImagePlus.COLOR_256 || refType == ImagePlus.COLOR_RGB)
            return IJ.error("Stack Rotate", "The selected reference image is not grayscale.")

          val stackType = stackImage.getType()

          if (stackType == ImagePlus.COLOR_256 || stackType == ImagePlus.COLOR_RGB)
            return IJ.error("Stack Rotate", "The selected stack is not grayscale.")

          val outDir = Option(dialog.getNextString()).filter(_.trim.nonEmpty).map(Paths.get(_))

          if (outDir.exists(!Files.exists(_)))
            return IJ.error("Stack Rotate", s"The specified output path does not exist.")

          if (outDir.exists(!Files.isDirectory(_)))
            return IJ.error("Stack Rotate", s"The specified output path is not a directory.")

          val out = outDir.map(dir => new PrintWriter(dir.resolve(s"${stackImage.getTitle()}.csv").toFile))

          val stack = stackImage.getImageStack()
          val slices = stack.size()

          IJ.showProgress(0, slices + 1)

          val refPrepared = prepareImage(refImage.getProcessor())
          val refAngle = getAngle(refPrepared)

          IJ.showProgress(1, slices + 1)

          for (i <- 1 to slices) {
            val processor = stack.getProcessor(i)
            val prepared = prepareImage(processor)

            val angle = getAngle(prepared)
            val angleDiff = angle - refAngle

            if (angleDiff.abs >= minAngle) {
              processor.rotate(angleDiff)

              out.foreach(_.println(angleDiff))
            } else {
              out.foreach(_.println(0))
            }

            IJ.showProgress(i + 1, slices + 1)
          }

          out.foreach(_.close())
      }

      stackImage.updateAndDraw()
    }
  }

  private def prepareImage(processor: ImageProcessor): ImageProcessor = {
    processor.setInterpolationMethod(ImageProcessor.BICUBIC)
    processor.setBackgroundColor(Color.BLACK)

    val copy = processor.convertToByteProcessor()

    new Auto_Local_Threshold().exec(new ImagePlus("", copy), "Bernsen", 15, 31, 15, true)

    copy
  }

  private def getAngle(processor: ImageProcessor): Double = {
    val width = processor.getWidth()
    val height = processor.getHeight()

    val indices = getIndices(processor)

    val xs = indices.map(i => (i % width).toDouble)
    val ys = indices.map(i => (height - i / width).toDouble)

    val n = xs.size
    val xsum = xs.sum
    val ysum = ys.sum

    val slope = (n * xs.zip(ys).map(_ * _).sum - xsum * ysum) / (n * xs.map(x => x * x).sum - xsum * xsum)

    toDegrees(atan(slope))
  }

  private def getIndices(processor: ImageProcessor): Seq[Int] = {
    val pixels = processor.getPixels().asInstanceOf[Array[Byte]]

    pixels.indices.filter(pixels(_) == -1)
  }
}
