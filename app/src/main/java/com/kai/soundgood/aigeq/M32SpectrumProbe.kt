package com.kai.soundgood.aigeq
import java.net.*
import java.nio.*
class M32SpectrumProbe : java.io.Closeable {
 private val socket=DatagramSocket().apply { connect(InetAddress.getByName("192.168.0.100"),10023);soTimeout=500 }
 private fun str(s:String):ByteArray {val b=(s+"\u0000").toByteArray(Charsets.UTF_8);return b+ByteArray((4-b.size%4)%4)}
 fun subscribe() {
  for(path in listOf("/meters/15","/meters/16")) {
   val b=str("/meters")+str(",s")+str(path)
   socket.send(DatagramPacket(b,b.size))
  }
 }
 fun read():String? {
  val p=DatagramPacket(ByteArray(4096),4096)
  try {socket.receive(p)} catch(e:SocketTimeoutException){return null}
  return try {
   val b=ByteBuffer.wrap(p.data,0,p.length).order(ByteOrder.BIG_ENDIAN)
   fun readStr():String {val start=b.position();while(b.hasRemaining()&&b.get()!=0.toByte()){};val end=b.position()-1;require(end>=start&&p.data[end]==0.toByte());b.position((b.position()+3) and -4);return String(p.data,start,end-start,Charsets.UTF_8)}
   val path=readStr();require(path=="/meters/15"||path=="/meters/16");require(readStr()==",b")
   val len=b.int;require(len>=4&&len<=b.remaining());b.order(ByteOrder.LITTLE_ENDIAN)
   val count=b.int
   "$path · blob=$len · header=$count · format unverified"
  } catch(e:Exception){null}
 }
 override fun close(){socket.close()}
}
