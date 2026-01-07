package org.ersucc.stackrotate

import fiji.threshold.Auto_Local_Threshold

import ij.{ IJ, ImagePlus, WindowManager }
import ij.gui.GenericDialog
import ij.plugin.PlugIn
import ij.process.ImageProcessor

import java.awt.Color

import scala.math.{ atan, toDegrees }

class StackRotate extends PlugIn {
  override def run(arg: String): Unit = {
    if (WindowManager.getImageCount() == 0)
      return IJ.error("Stack Rotate", "No images open.")

    val dialog = new GenericDialog("Stack Rotate")

    dialog.addImageChoice("Select reference image:", null)
    dialog.addImageChoice("Select image/stack to align:", null)
    dialog.addCheckbox("Write transformations to log", false)
    dialog.showDialog()

    if (dialog.wasOKed) {
      val refImage = dialog.getNextImage()
      val stackImage = dialog.getNextImage()
      val log = dialog.getNextBoolean()

      val refType = refImage.getType()

      if (refType == ImagePlus.COLOR_256 || refType == ImagePlus.COLOR_RGB)
        return IJ.error("Stack Rotate", "The selected reference image is not grayscale.")

      val stackType = stackImage.getType()

      if (stackType == ImagePlus.COLOR_256 || stackType == ImagePlus.COLOR_RGB)
        return IJ.error("Stack Rotate", "The selected stack is not grayscale.")

      val stack = stackImage.getImageStack()
      val slices = stack.size()

      IJ.showProgress(0, slices + 1)

      val refPrepared = prepareImage(refImage.getProcessor())
      val refAngle = getAngle(refPrepared)
      val (refX, refY) = getCenter(refPrepared)

      if (log)
        IJ.log(s"Reference:\nRotation: $refAngle degrees\nCenter X: $refX pixels\nCenter Y: $refY pixels")

      IJ.showProgress(1, slices + 1)

      for (i <- 1 to slices) {
        val processor = stack.getProcessor(i)
        val prepared = prepareImage(processor)

        val angle = getAngle(prepared)
        val angleDiff = angle - refAngle

        processor.rotate(angleDiff)

        val (x, y) = getCenter(prepared)
        val xDiff = refX - x
        val yDiff = refY - y

        processor.translate(xDiff, yDiff)

        if (log)
          IJ.log(s"Slice $i:\nRotation: $angleDiff degrees\nTranslation X: $xDiff pixels\nTranslation Y: $yDiff pixels")

        IJ.showProgress(i + 1, slices + 1)
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

  private def getCenter(processor: ImageProcessor): (Int, Int) = {
    val width = processor.getWidth()

    val indices = getIndices(processor)

    val xs = indices.map(_ % width)
    val ys = indices.map(_ / width)

    (xs.sum / xs.size, ys.sum / ys.size)
  }

  private def getIndices(processor: ImageProcessor): Seq[Int] = {
    val pixels = processor.getPixels().asInstanceOf[Array[Byte]]

    pixels.indices.filter(pixels(_) == -1)
  }
}
