import com.kai.soundgood.aigeq.*
fun main() {
    check(M32TargetValidation.valid(EqTarget(TargetKind.CHANNEL,32)))
    check(!M32TargetValidation.valid(EqTarget(TargetKind.CHANNEL,33)))
    check(!M32TargetValidation.valid(EqTarget(TargetKind.BUS,0)))
    check(M32TargetValidation.valid(EqTarget(TargetKind.MATRIX,6)))
    check(OscReplyDecoder.decode(byteArrayOf(1,2,3)) == null)
    val t=EqTarget(TargetKind.BUS,1)
    val snapshot=M32PeqSnapshot(t,mapOf("/bus/01/eq/on" to listOf(1)),listOf("/bus/01/eq/1/f"),0,false)
    val r=M32Diagnostics.summarize(snapshot)
    check(r.status=="INCOMPLETE_READ_ONLY" && r.received==1 && r.expected==2)
    println("PASS: target bounds, malformed OSC, incomplete read-only diagnostics")
}
