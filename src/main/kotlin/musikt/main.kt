package musikt

import java.io.File
import kotlin.script.experimental.host.toScriptSource

fun main(vararg args: String) {
    evaluateMusiKts(File(args[0]).toScriptSource()).save(File(args[0]+".wav"))
}