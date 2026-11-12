package com.kai.soundgood.aigeq

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** Minimal strict OSC message decoder. Bundles and unknown tags are rejected, never guessed. */
data class OscReply(val address: String, val arguments: List<Any>)
object OscReplyDecoder {
    fun decode(bytes: ByteArray): OscReply? = try {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        fun readString(): String {
            val start = b.position()
            var end = start
            while (end < b.limit() && bytes[end] != 0.toByte()) end++
            require(end < b.limit())
            val value = String(bytes, start, end - start, StandardCharsets.UTF_8)
            val next = (end + 4) and -4
            require(next <= b.limit())
            b.position(next)
            return value
        }
        val address = readString()
        require(address.startsWith("/") && address != "#bundle")
        val tags = if (b.hasRemaining()) readString() else ""
        require(tags.isEmpty() || tags.startsWith(","))
        val args = tags.drop(1).map { tag ->
            when (tag) {
                'f' -> b.float
                'i' -> b.int
                's' -> readString()
                'T' -> true
                'F' -> false
                else -> error("Unsupported OSC tag")
            }
        }
        OscReply(address, args)
    } catch (_: Exception) { null }
}

data class M32PeqSnapshot(
    val target: EqTarget,
    val values: Map<String, List<Any>>,
    val missing: List<String>,
    val unexpected: Int,
    val complete: Boolean
)

/** Read-only inspection. A complete query response is NOT a verified routing or write capability. */
class M32LiveStateReader(private val osc: OscClient) {
    fun inspectPeq(target: EqTarget): M32PeqSnapshot {
        require(M32TargetValidation.valid(target)) { "Invalid M32 target: ${target.label}" }
        val prefix = when (target.kind) {
            TargetKind.CHANNEL -> "/ch/%02d/eq".format(target.number)
            TargetKind.BUS -> "/bus/%02d/eq".format(target.number)
            TargetKind.MAIN_LR -> "/main/st/eq"
            TargetKind.MATRIX -> "/mtx/%02d/eq".format(target.number)
        }
        val paths = buildList {
            add("$prefix/on")
            for (band in 1..target.peqBands) {
                add("$prefix/$band/f")
                add("$prefix/$band/g")
                add("$prefix/$band/q")
            }
        }
        val received = mutableMapOf<String, List<Any>>()
        var unexpected = 0
        // The transport is shared with the connection probe: perform this scan serially.
        for (path in paths) {
            osc.sendQuery(path)
            val deadline = System.nanoTime() + 250_000_000L
            while (System.nanoTime() < deadline && path !in received) {
                val packet = osc.receive() ?: break
                val reply = OscReplyDecoder.decode(packet)
                if (reply != null && reply.address in paths && reply.arguments.isNotEmpty()) {
                    received[reply.address] = reply.arguments
                } else unexpected++
            }
        }
        val missing = paths.filterNot { it in received }
        return M32PeqSnapshot(target, received.toMap(), missing, unexpected, missing.isEmpty())
    }
}
