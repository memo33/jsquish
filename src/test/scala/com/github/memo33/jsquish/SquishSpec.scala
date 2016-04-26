//package com.github.memo33.jsquish
//
//import org.scalatest.{WordSpec, Matchers, PrivateMethodTester}
//import gr.zdimensions.jsquish.{Squish => SquishOld}
//
//class SquishSpec extends WordSpec with Matchers with PrivateMethodTester {
//
//  val imageToByteArray = PrivateMethod[Array[Byte]]('imageToByteArray)
//  def decompress(arr: Array[Byte], w: Int, h: Int): Array[Byte] = {
//    Fsh.FshFormat.Dxt3 invokePrivate imageToByteArray(DxtDecoding.decode(Fsh.FshFormat.Dxt3, arr, 0, w, h))
//  }
//
//  val w, h = 16
//  val compressors1 = for {
//    dxt <- Seq(Squish.CompressionType.DXT3, Squish.CompressionType.DXT1)
//    fit <- Seq(Squish.CompressionMethod.CLUSTER_FIT, Squish.CompressionMethod.RANGE_FIT)
//  } yield { arr: Array[Byte] =>
//    Squish.compressImage(arr, w, h, null, dxt, fit)
//  }
//  val compressors2 = for {
//    dxt <- Seq(SquishOld.CompressionType.DXT3, SquishOld.CompressionType.DXT1)
//    fit <- Seq(SquishOld.CompressionMethod.CLUSTER_FIT, SquishOld.CompressionMethod.RANGE_FIT)
//  } yield { arr: Array[Byte] =>
//    SquishOld.compressImage(arr, w, h, null, dxt, fit)
//  }
//
//  // construct a few test 'pictures'
//  val arrs = (Seq.newBuilder[Array[Byte]]
//    += Array.fill[Byte](4 * w * h)(0)
//    += Array.fill[Byte](4 * w * h)(-1)
//    += Array.fill[Byte](4 * w * h)(42)
//    += Array.iterate[Byte](0.toByte, 4 * w * h)(x => (x + 1).toByte)
//    += Array.tabulate[Byte](4 * w * h)(i => (42 + i / 15).toByte)
//    += Array.tabulate[Byte](4 * w * h)(i => (42 + i / 33).toByte)
//    += {
//      val rand = new util.Random()
//      val a = new Array[Byte](4 * w * h)
//      rand.nextBytes(a)
//      a
//    }
//  ).result
//
//  "Squish reimplementation" should {
////    "agree with old implementation" in {
////      for ((comp1, comp2) <- compressors1 zip compressors2; a <- arrs) {
////        comp1(a) shouldBe comp2(a)
////      }
////    }
//    "be invariant on already compressed images" in {
//      val comp = { arr: Array[Byte] =>
//        Squish.compressImage(arr, w, h, null, Squish.CompressionType.DXT3, Squish.CompressionMethod.CLUSTER_FIT)
//      }
//      for (a <- arrs) withClue("Original array: " + a.deep) {
//        val b = decompress(comp(a), w, h)
//        decompress(comp(b), w, h) shouldBe b
//      }
//    }
//  }
//
//}
