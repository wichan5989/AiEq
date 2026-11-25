package com.kai.soundgood.aigeq

import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

/** Dedicated read-only M32 meter socket; does not share PEQ command replies. */
class M32MeterReader(private val host:String="192.168.0.100") : java.io.Closeable {
 private val socket=DatagramSocket().apply { connect(InetAddress.getByName(host),10023); soTimeout=400 }
 private fun osc(s:String):ByteArray { val b=(s+"\u0000").toByteArray(Charsets.UTF_8);return b+ByteArray((4-b.size%4)%4) }
 fun subscribe(bus:Boolean) {
  val path=if(bus) "/meters/2" else "/meters/1"
  val bytes=osc("/meters")+osc(",s")+osc(path)
  socket.send(DatagramPacket(bytes,bytes.size))
 }
 fun read(bus:Boolean):List<Float>? {
  val packet=DatagramPacket(ByteArray(4096),4096)
  try {socket.receive(packet)} catch(e:java.net.SocketTimeoutException){return null}
  return decode(packet.data.copyOf(packet.length),bus)
 }
 override fun close(){socket.close()}
 companion object {
  fun decode(bytes:ByteArray,bus:Boolean):List<Float>? = try {
   val header=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
   fun string():String {
    val start=header.position()
    while(header.hasRemaining() && header.get()!=0.toByte()) {}
    val end=header.position()-1
    require(end>=start && bytes[end]==0.toByte())
    header.position((header.position()+3) and -4)
    return String(bytes,start,end-start,Charsets.UTF_8)
   }
   require(string()==if(bus) "/meters/2" else "/meters/1")
   require(string()==",b")
   val length=header.int
   require(length==header.remaining())
   header.order(ByteOrder.LITTLE_ENDIAN)
   val count=header.int
   require(count==if(bus) 49 else 96)
   require(length==4+count*4)
   val values=List(if(bus) 16 else 32) {header.float}
   require(values.all { it.isFinite() && it>=0f && it<=8f })
   values.map { (20.0*log10(it.toDouble().coerceAtLeast(0.000001))).toFloat() }
  }catch(_:Exception){null}
 }
}
