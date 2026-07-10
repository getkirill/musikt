package musikt

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

@KotlinScript(fileExtension = "musi.kts", compilationConfiguration = AudioScriptCompilationConfiguration::class)
abstract class AudioScriptEnv
object AudioScriptCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
    defaultImports(
        "musikt.*",
    )
    implicitReceivers(AudioDsl::class)
})

class AudioScriptEvaluationConfiguration(val dsl: AudioDsl) : ScriptEvaluationConfiguration({
    implicitReceivers(dsl)
})

fun evaluateMusiKts(source: SourceCode): Audio {
    val instance = AudioDsl()
    val host = BasicJvmScriptingHost()
    val result = host.eval(source, AudioScriptCompilationConfiguration, AudioScriptEvaluationConfiguration(instance))
    return when (result) {
        is ResultWithDiagnostics.Success -> {
            Audio(instance.synthesize(), instance.sampleRate)
        }

        is ResultWithDiagnostics.Failure -> {
            throw RuntimeException("Script error: " + result.reports.joinToString { it.message })
        }
    }
}