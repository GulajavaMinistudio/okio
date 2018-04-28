/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import org.junit.Test

import okio.ByteString.Companion.encodeUtf8
import okio.TestUtil.assertEquivalent
import okio.TestUtil.bufferWithSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import kotlin.test.assertNull

/** Tests behavior optimized by sharing segments between buffers and byte strings.  */
class SegmentSharingTest {
  @Test fun snapshotOfEmptyBuffer() {
    val snapshot = Buffer().snapshot()
    assertEquivalent(snapshot, ByteString.EMPTY)
  }

  @Test fun snapshotsAreEquivalent() {
    val byteString = bufferWithSegments(xs, ys, zs).snapshot()
    assertEquivalent(byteString, bufferWithSegments(xs, ys + zs).snapshot())
    assertEquivalent(byteString, bufferWithSegments(xs + ys + zs).snapshot())
    assertEquivalent(byteString, (xs + ys + zs).encodeUtf8())
  }

  @Test fun snapshotGetByte() {
    val byteString = bufferWithSegments(xs, ys, zs).snapshot()
    assertEquals('x', byteString.getByte(0).toChar())
    assertEquals('x', byteString.getByte(xs.length - 1).toChar())
    assertEquals('y', byteString.getByte(xs.length).toChar())
    assertEquals('y', byteString.getByte(xs.length + ys.length - 1).toChar())
    assertEquals('z', byteString.getByte(xs.length + ys.length).toChar())
    assertEquals('z', byteString.getByte(xs.length + ys.length + zs.length - 1).toChar())
    try {
      byteString.getByte(-1)
      fail()
    } catch (expected: IndexOutOfBoundsException) {
    }

    try {
      byteString.getByte(xs.length + ys.length + zs.length)
      fail()
    } catch (expected: IndexOutOfBoundsException) {
    }
  }

  @Test fun snapshotWriteToOutputStream() {
    val byteString = bufferWithSegments(xs, ys, zs).snapshot()
    val out = Buffer()
    byteString.write(out.outputStream())
    assertEquals(xs + ys + zs, out.readUtf8())
  }

  /**
   * Snapshots share their backing byte arrays with the source buffers. Those byte arrays must not
   * be recycled, otherwise the new writer could corrupt the segment.
   */
  @Test fun snapshotSegmentsAreNotRecycled() {
    val buffer = bufferWithSegments(xs, ys, zs)
    val snapshot = buffer.snapshot()
    assertEquals(xs + ys + zs, snapshot.utf8())

    // While locking the pool, confirm that clearing the buffer doesn't release its segments.
    synchronized(SegmentPool::class.java) {
      SegmentPool.next = null
      SegmentPool.byteCount = 0L
      buffer.clear()
      assertNull(SegmentPool.next)
    }
  }

  /**
   * Clones share their backing byte arrays with the source buffers. Those byte arrays must not
   * be recycled, otherwise the new writer could corrupt the segment.
   */
  @Test fun cloneSegmentsAreNotRecycled() {
    val buffer = bufferWithSegments(xs, ys, zs)
    val clone = buffer.clone()

    // While locking the pool, confirm that clearing the buffer doesn't release its segments.
    synchronized(SegmentPool::class.java) {
      SegmentPool.next = null
      SegmentPool.byteCount = 0L
      buffer.clear()
      assertNull(SegmentPool.next)
      clone.clear()
      assertNull(SegmentPool.next)
    }
  }

  @Test fun snapshotJavaSerialization() {
    val byteString = bufferWithSegments(xs, ys, zs).snapshot()
    assertEquivalent(byteString, TestUtil.reserialize(byteString))
  }

  @Test fun clonesAreEquivalent() {
    val bufferA = bufferWithSegments(xs, ys, zs)
    val bufferB = bufferA.clone()
    assertEquivalent(bufferA, bufferB)
    assertEquivalent(bufferA, bufferWithSegments(xs + ys, zs))
  }

  /** Even though some segments are shared, clones can be mutated independently.  */
  @Test fun mutateAfterClone() {
    val bufferA = Buffer()
    bufferA.writeUtf8("abc")
    val bufferB = bufferA.clone()
    bufferA.writeUtf8("def")
    bufferB.writeUtf8("DEF")
    assertEquals("abcdef", bufferA.readUtf8())
    assertEquals("abcDEF", bufferB.readUtf8())
  }

  @Test fun concatenateSegmentsCanCombine() {
    val bufferA = Buffer().writeUtf8(ys).writeUtf8(us)
    assertEquals(ys, bufferA.readUtf8(ys.length.toLong()))
    val bufferB = Buffer().writeUtf8(vs).writeUtf8(ws)
    val bufferC = bufferA.clone()
    bufferA.write(bufferB, vs.length.toLong())
    bufferC.writeUtf8(xs)

    assertEquals(us + vs, bufferA.readUtf8())
    assertEquals(ws, bufferB.readUtf8())
    assertEquals(us + xs, bufferC.readUtf8())
  }

  @Test fun shareAndSplit() {
    val bufferA = Buffer().writeUtf8("xxxx")
    val snapshot = bufferA.snapshot() // Share the segment.
    val bufferB = Buffer()
    bufferB.write(bufferA, 2) // Split the shared segment in two.
    bufferB.writeUtf8("yy") // Append to the first half of the shared segment.
    assertEquals("xxxx", snapshot.utf8())
  }

  @Test fun appendSnapshotToEmptyBuffer() {
    val bufferA = bufferWithSegments(xs, ys)
    val snapshot = bufferA.snapshot()
    val bufferB = Buffer()
    bufferB.write(snapshot)
    assertEquivalent(bufferB, bufferA)
  }

  @Test fun appendSnapshotToNonEmptyBuffer() {
    val bufferA = bufferWithSegments(xs, ys)
    val snapshot = bufferA.snapshot()
    val bufferB = Buffer().writeUtf8(us)
    bufferB.write(snapshot)
    assertEquivalent(bufferB, Buffer().writeUtf8(us + xs + ys))
  }

  @Test fun copyToSegmentSharing() {
    val bufferA = bufferWithSegments(ws, xs + "aaaa", ys, "bbbb$zs")
    val bufferB = bufferWithSegments(us)
    bufferA.copyTo(bufferB, (ws.length + xs.length).toLong(), (4 + ys.length + 4).toLong())
    assertEquivalent(bufferB, Buffer().writeUtf8(us + "aaaa" + ys + "bbbb"))
  }
}

private val us = "u".repeat(Segment.SIZE / 2 - 2)
private val vs = "v".repeat(Segment.SIZE / 2 - 1)
private val ws = "w".repeat(Segment.SIZE / 2)
private val xs = "x".repeat(Segment.SIZE / 2 + 1)
private val ys = "y".repeat(Segment.SIZE / 2 + 2)
private val zs = "z".repeat(Segment.SIZE / 2 + 3)
