package com.kai.soundgood.aigeq
import java.net.*
import java.nio.*
class M32SpectrumProbe : java.io.Closeable {
 private val socket=DatagramSocket().apply { connect(InetAddress.getByName("192.168.0.100"),10023);soTimeout=500 }
 private fun str(s:String):ByteArray {val b=(s+"\u0000").toByteArray(Charsets.UTF_8);return b+ByteArray((4-b.size%4)%4)}
 var sourceLabel:String="UNKNOWN"
  private set
 fun selectSource(source:Int,post:Boolean) {
  require(source in 2..72)
  fun send(path:String,value:Int) {
   val bytes=str(path)+str(",i")+ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
   socket.send(DatagramPacket(bytes,bytes.size))
  }
  sourceLabel="UNKNOWN"
  send("/-prefs/rta/source",source)
  send("/-prefs/rta/pos",if(post) 1 else 0)
 }
 fun subscribe() {
  val query=str("/-stat/rtasource");socket.send(DatagramPacket(query,query.size))
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
   val path=readStr()
   if(path=="/-stat/rtasource") {
    require(readStr()==",i");val raw=b.int
    val post=raw in 98..170;val n=if(post) raw-98 else raw
    sourceLabel=when(n){in 0..31->"CH ${n+1}";in 32..39->"AUX ${n-31}";in 40..47->"FX RETURN ${n-39}";in 48..63->"BUS ${n-47}";in 64..69->"MATRIX ${n-63}";70->"LR";71->"MONO";72->"MONITOR";else->"UNKNOWN ($raw)"}+if(post) " POST" else " PRE"
    return null
   }
   require(path=="/meters/15");require(readStr()==",b")
   val len=b.int;require(len>=4&&len<=b.remaining());b.order(ByteOrder.LITTLE_ENDIAN)
   val count=b.int
   require(len==204 && count==50 && b.remaining()>=200)
   List(100) { (b.short.toInt()/256f).also { require(it<=0f) } }
  } catch(e:Exception){null}
 }
 override fun close(){socket.close()}
}
