import com.kai.soundgood.aigeq.*
fun main() {
 val g=LiveAudioSessionGuard(1200)
 check(!g.status(0).canUseForAdvice)
 g.begin(); g.completeFrame(100); check(!g.status(100).canUseForAdvice)
 g.routeVerified(true); g.completeFrame(200)
 check(g.status(1400).canUseForAdvice)
 check(!g.status(1401).canUseForAdvice)
 g.routeVerified(false); check(!g.status(250).canUseForAdvice)
 g.routeVerified(true); check(!g.status(260).canUseForAdvice)
 g.completeFrame(300); check(g.status(301).canUseForAdvice)
 g.end(); check(!g.status(302).canUseForAdvice)
 println("PASS: 9 live-session safety assertions")
}
