package com.kai.soundgood.aigeq
import java.net.*
import java.nio.*
class M32SpectrumProbe : java.io.Closeable {
 private val socket=DatagramSocket().apply { connect(InetAddress.getByName("192.168.0.100"),10023);soTimeout=500 }
 private fun str(s:String):ByteArray {val b=(s+"\u0000").toByteArray(Charsets.UTF_8);return b+ByteArray((4-b.size%4)%4)}
 fun subscribe() {
  for(path in listOf("/meters/15")) {
   val b=str("/meters")+str(",s")+str(path)
   socket.send(DatagramPacket(b,b.size))
  }
 }
 fun read():List<Float>? {
  val p=DatagramPacket(ByteArray(4096),4096)
  try {socket.receive(p)} catch(e:SocketTimeoutException){return null}
  return try {
   val b=ByteBuffer.wrap(p.data,0,p.length).order(ByteOrder.BIG_ENDIAN)
   fun readStr():String {val start=b.position();while(b.hasRemaining()&&b.get()!=0.toByte()){};val end=b.position()-1;require(end>=start&&p.data[end]==0.toByte());b.position((b.position()+3) and -4);return String(p.data,start,end-start,Charsets.UTF_8)}
   val path=readStr();require(path=="/meters/15");require(readStr()==",b")
   val len=b.int;require(len>=4&&len<=b.remaining());b.order(ByteOrder.LITTLE_ENDIAN)
   val count=b.int
   require(len==204 && count==50 && b.remaining()>=200)
   List(100) { (b.short.toInt()/256f).also { require(it<=0f) } }
  } catch(e:Exception){null}
 }
 override fun close(){socket.close()}
}
