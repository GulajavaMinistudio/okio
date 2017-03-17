import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.cos
import java.lang.Math.sin
import javax.imageio.ImageIO

class LineDetector

fun main(args: Array<String>) {
  for (file in listOf("wikipedia.png", "wikipedia90.png")) {
    val image = BufferedImageBmp(ImageIO.read(File(file)))

    val edges = BufferedImageBmp(BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_RGB))
    image.detectEdges(Orientation.HORIZONTAL, edges)
    ImageIO.write(edges.bufferedImage, "png", File("${file.substring(0, file.length - 4)}_edges.png"))

    val maxR = Math.max(image.width(), image.height()) * 2
    val thetaValues = 180
    val hough = BufferedImageBmp(BufferedImage(thetaValues, maxR, BufferedImage.TYPE_INT_RGB))
    edges.hough(Orientation.HORIZONTAL, hough)
    ImageIO.write(hough.bufferedImage, "png", File("${file.substring(0, file.length - 4)}_hough.png"))
  }
}

enum class Orientation { HORIZONTAL, VERTICAL }

class BufferedImageBmp(val bufferedImage: BufferedImage) : GreyscaleBitmap() {
  // TODO: yolo red only
  override fun get(x: Int, y: Int): Int = (bufferedImage.getRGB(x, y) shr 16) and 0xff

  override fun set(x: Int, y: Int, c: Int) {
    val r = c and 0xff
    bufferedImage.setRGB(x, y, r or (r shl 8) or (r shl 16))
  }

  override fun width(): Int = bufferedImage.width

  override fun height(): Int = bufferedImage.height
}

abstract class GreyscaleBitmap {
  abstract fun get(x: Int, y: Int) : Int
  abstract fun set(x: Int, y: Int, c: Int)
  abstract fun width() : Int
  abstract fun height() : Int

  fun detectEdges(orientation: Orientation, output: GreyscaleBitmap) {
    for (y in 1 until height() - 1) {
      for (x in 1 until width() - 1) {
        val accumulator = when (orientation) {
          Orientation.HORIZONTAL -> {
            /*
              -1 0 1
              -2 0 2
              -1 0 1
            */
            (-1 * get(x - 1, y - 1)
                + 1 * get(x + 1, y - 1)
                + -2 * get(x - 1, y)
                + 2 * get(x + 1, y)
                + -1 * get(x - 1, y + 1)
                + 1 * get(x + 1, y + 1))
          }
          Orientation.VERTICAL -> {
            /*
              -1 -2 -1
               0  0  0
               1  2  1
             */
            TODO()
          }
        }

        output.set(x, y, accumulator.clamp(0, 255))
      }
    }
  }

  fun hough(orientation: Orientation, accumulator: GreyscaleBitmap) {
    val step = Math.PI / accumulator.width()
    for (y in 0 until height()) {
      for (x in 0 until width()) {
        val v = get(x, y)
        if (v < 128) continue

        for (thetaBucket in 0 until accumulator.width()) {
          val theta = thetaBucket * step
          val r = (computeR(orientation, theta, x, y) + accumulator.height() / 2).clamp(0, accumulator.height() - 1)
          accumulator.set(thetaBucket, r, (accumulator.get(thetaBucket, r) + 1).clamp(0, 255))
        }
      }
    }

  }

  private fun computeR(orientation: Orientation, theta: Double, x: Int, y: Int): Int {
    return (x * cos(theta) + y * sin(theta)).toInt()
  }
}

fun Int.clamp(min: Int, max: Int) : Int {
  if (this < min) return min
  if (this > max) return max
  return this
}
