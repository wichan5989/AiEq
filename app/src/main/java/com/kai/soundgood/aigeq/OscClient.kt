package com.kai.soundgood.aigeq

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** M32 UDP OSC: transport only. Never guesses GEQ FX parameter addresses. */
class OscClient(private val host: String = "192.168.0.100", private val port: Int = 10023) {
 private var socket: DatagramSocket? = null
 fun connect() { if (socket == null) socket = DatagramSocket().apply { soTimeout = 800 } }
 fun close() { socket?.close(); socket = null }
 private fun oscString(s: String): ByteArray {
  val b = (s + "\u0000").toByteArray(StandardCharsets.UTF_8)
  return b + ByteArray((4 - b.size % 4) % 4)
 }
 fun sendQuery(address: String) = send(address, null)
 fun sendFloat(address: String, value: Float) = send(address, value)
 private fun send(address: String, value: Float?) {
  require(address.startsWith("/"))
  val out = oscString(address) + (if (value == null) byteArrayOf() else oscString(",f") + ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array())
  val s = socket ?: error("OSC not connected")
  s.send(DatagramPacket(out, out.size, InetAddress.getByName(host), port))
 }
 fun receive(): ByteArray? {
  val s = socket ?: return null
  return try { val b = ByteArray(4096); val p = DatagramPacket(b,b.size); s.receive(p); p.data.copyOf(p.length) } catch (_: java.net.SocketTimeoutException) { null }
 }
 companion object {
  fun addressOf(packet: ByteArray): String = packet.takeWhile { it.toInt() != 0 }.toByteArray().toString(StandardCharsets.UTF_8)
 }
}
