import com.kai.soundgood.aigeq.*
fun main() {
 val off = LiveReadiness.evaluate(ReadinessInput(false,false,AudioFeed.ANDROID_MIC,true,false,false))
 check(!off.canAnalyze && !off.canWrite && off.warnings.size >= 4)
 val mic = LiveReadiness.evaluate(ReadinessInput(true,true,AudioFeed.ANDROID_MIC,true,false,true))
 check(mic.canAnalyze && !mic.canWrite)
 check(mic.warnings.any { it.contains("UI-only") })
 val verified = LiveReadiness.evaluate(ReadinessInput(true,true,AudioFeed.VERIFIED_MIXER_STREAM,true,true,true))
 check(verified.canAnalyze && !verified.canWrite)
 println("Readiness smoke tests passed: 3 cases")
}
