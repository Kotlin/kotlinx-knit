/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import kotlin.math.*
import kotlin.properties.*

fun formatDiff(oldLines: List<String>, newLines: List<String>): String {
    val diff = computeDiff(oldLines, newLines)
    val out = ArrayList<String>(diff.size * 2)
    var pOp: DiffOp? = null
    var pPos = 0
    var pHdr = -1
    var d1: Diff by Delegates.notNull()
    var d2: Diff by Delegates.notNull()

    fun flushHeader() {
        if (pHdr < 0) return
        out[pHdr] = formatHeader(d1, d2)
        pHdr = -1
    }

    for (d in diff) {
        val pos = if (d.op == DiffOp.DELETE) d.x else d.y
        if (d.op != pOp || pos != pPos + 1) {
            if (pOp == DiffOp.DELETE && d.op == DiffOp.INSERT && d.x == d2.x) {
                d1 = Diff(d1.x, d.y, DiffOp.CHANGE, "")
                d2 = d
                out.add("---")
            } else {
                flushHeader()
                d1 = d
                d2 = d
                pHdr = out.size
                out.add("")
            }
        } else {
            d2 = d
        }
        out.add("${d.op.ch} ${d.s}")
        pOp = d.op
        pPos = pos
    }

    flushHeader()
    return out.joinToString("\n")
}

private fun formatHeader(d1: Diff, d2: Diff): String {
    val xr = formatRange(d1.x + 1, d2.x + 1)
    val yr = formatRange(d1.y + 1, d2.y + 1)
    return "$xr${d1.op.md}$yr"
}

private fun formatRange(x1: Int, x2: Int): String =
    if (x1 == x2) x1.toString() else "$x1,$x2"

private fun computeDiff(xs: List<String>, ys: List<String>): List<Diff> {
    // trim common prefix
    val maxPre = min(xs.size, ys.size)
    var pre = 0
    while (pre < maxPre && xs[pre] == ys[pre]) pre++
    // trim common suffix
    var suf = 0
    val maxSuf = maxPre - pre
    while (suf < maxSuf && xs[xs.size - 1 - suf] == ys[ys.size - 1 - suf]) suf++
    // compute diff
    return computeDiffImpl(xs, pre, xs.size - suf, ys, pre, ys.size - suf)
}

// An O(ND) Difference Algorithm and Its Variations* EUGENE W. MYERS, Greedy algorithm
private fun computeDiffImpl(xs: List<String>, x1: Int, x2: Int, ys: List<String>, y1: Int, y2: Int): List<Diff> {
    val xl = x2 - x1
    val yl = y2 - y1
    // fast paths
    if (xl == 0) return (y1 until y2).map { y -> Diff(x1 - 1, y, DiffOp.INSERT, ys[y]) }
    if (yl == 0) return (x1 until x2).map { x -> Diff(x, y1 - 1, DiffOp.DELETE, xs[x]) }
    // full algo
    val mx = xl + yl
    val kd = y1 - x1
    val vs = ArrayList<IntArray>(mx + 1)
    var vp = IntArray(1)
    vp[0] = x1
    // d -- delta we're looking at
    for (d in 0..mx) {
        val dp = d - 1
        val vd = IntArray(2 * d + 1)
        vs.add(vd)
        // k -- diagonal index == x - y + kd
        for (k in -d..d step 2) {
            var x = if (k == -d || k != d && vp[dp + k - 1] < vp[dp + k + 1]) vp[dp + k + 1] else vp[dp + k - 1] + 1
            var y = x + kd - k
            while (x < x2 && y < y2 && xs[x] == ys[y]) {
                x++
                y++
            }
            vd[d + k] = x
            if (x >= x2 && y >= y2) {
                // found an optimal diff
                assert(x == x2 && y == y2)
                return buildDiff(xs, x1, ys, y1, d, k, vs)
            }
        }
        vp = vd
    }
    error("This cannot happen")
}

private fun buildDiff(xs: List<String>, x1: Int, ys: List<String>, y1: Int, d0: Int, k0: Int, vs: ArrayList<IntArray>): List<Diff> {
    val diff = ArrayList<Diff>(d0)
    val kd = y1 - x1
    var d = d0
    var k = k0
    while (d > 0) {
        val dp = d - 1
        val vp = vs[dp]
        if (k == -d || k != d && vp[dp + k - 1] < vp[dp + k + 1]) {
            val x = vp[dp + k + 1] - 1
            val y = x + kd - k
            diff.add(Diff(x, y, DiffOp.INSERT, ys[y]))
            k++
        } else {
            val x = vp[dp + k - 1]
            val y = x + kd - k
            diff.add(Diff(x, y, DiffOp.DELETE, xs[x]))
            k--
        }
        d--
    }
    diff.reverse()
    return diff
}


private enum class DiffOp(val ch: Char, val md: Char) {
    INSERT('>', 'a'),
    DELETE('<', 'd'),
    CHANGE('-', 'c')
}

private data class Diff(
    val x: Int,
    val y: Int,
    val op: DiffOp,
    val s: String
)
