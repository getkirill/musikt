package musikt

import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow


class SoundFont2(val file: File) {
    lateinit var name: String private set

    var sampleData: ShortArray = ShortArray(0)
        private set
    val presets = mutableListOf<Preset>()
    val instruments = mutableListOf<Instrument>()
    val samples = mutableListOf<SampleHeader>()

    private val rawPbags = mutableListOf<RawBag>()
    private val rawPgens = mutableListOf<RawGenMod>()
    private val rawIbags = mutableListOf<RawBag>()
    private val rawIgens = mutableListOf<RawGenMod>()

    init {
        file.inputStream().buffered().use { ist ->
            readChunk(ist)
        }
        buildHierarchy()
    }

    private fun readChunk(ist: BufferedInputStream): Int {
        val magicBytes = ist.readNBytes(4)
        if (magicBytes.size < 4) return 0
        val magicType = magicBytes.decodeToString()

        val length = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
        var consumed = 8 + length

        when (magicType) {
            "LIST", "RIFF" -> parseList(ist, length)
            "INAM" -> parseINAM(ist, length)
            "phdr" -> parsePhdr(ist, length)
            "pbag" -> parseBags(ist, length, rawPbags)
            "pgen" -> parseGenMods(ist, length, rawPgens)
            "inst" -> parseInst(ist, length)
            "ibag" -> parseBags(ist, length, rawIbags)
            "igen" -> parseGenMods(ist, length, rawIgens)
            "shdr" -> parseShdr(ist, length)
            "smpl" -> parseSmpl(ist, length)
            else -> {
                require(length >= 0) { "$magicType tried using negative length" }
                ist.skipNBytes(length.toLong())
            }
        }

        if (length % 2 == 1) {
            ist.skipNBytes(1)
            consumed++
        }
        return consumed
    }

    private fun parseSmpl(ist: BufferedInputStream, length: Int) {
        val totalSamples = length / 2
        val buffer = ByteBuffer.wrap(ist.readNBytes(length)).order(ByteOrder.LITTLE_ENDIAN)
        sampleData = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            sampleData[i] = buffer.short
        }
    }

    private fun parseList(ist: BufferedInputStream, length: Int) {
        val listType = ist.readNBytes(4).decodeToString()
        var read = 4
        while (read < length) {
            val chunkLength = readChunk(ist)
            if (chunkLength == 0) break
            read += chunkLength
        }
    }

    private fun parseINAM(ist: BufferedInputStream, length: Int) {
        val rawBytes = ist.readNBytes(length)
        this.name = rawBytes.takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
    }

    private fun parsePhdr(ist: BufferedInputStream, length: Int) {
        var read = 0
        while (read < length) {
            val name = ist.readNBytes(20).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
            val preset = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val bank = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val bagNdx = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val library = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val genre = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val morph = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int

            presets.add(Preset(name, preset, bank, bagNdx))
            read += 38
        }
    }

    private fun parseInst(ist: BufferedInputStream, length: Int) {
        var read = 0
        while (read < length) {
            val name = ist.readNBytes(20).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
            val bagNdx = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            instruments.add(Instrument(name, bagNdx))
            read += 22
        }
    }

    private fun parseBags(ist: BufferedInputStream, length: Int, target: MutableList<RawBag>) {
        var read = 0
        while (read < length) {
            val genNdx = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val modNdx = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            target.add(RawBag(genNdx, modNdx))
            read += 4
        }
    }

    private fun parseGenMods(ist: BufferedInputStream, length: Int, target: MutableList<RawGenMod>) {
        var read = 0
        while (read < length) {
            val code = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val amount = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short
            target.add(RawGenMod(code, amount))
            read += 4
        }
    }

    private fun parseShdr(ist: BufferedInputStream, length: Int) {
        var read = 0
        while (read < length) {
            val name = ist.readNBytes(20).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
            val start = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val end = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val startLoop = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val endLoop = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val sampleRate = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
            val originalPitch = ist.read().toInt()
            val pitchCorrection = ist.read().toByte().toInt()
            val sampleLink = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val sampleType = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            if (name.isNotEmpty() && name != "EOS") {
                samples.add(
                    SampleHeader(
                        name,
                        start,
                        end,
                        startLoop,
                        endLoop,
                        sampleRate,
                        originalPitch,
                        pitchCorrection,
                        sampleType
                    )
                )
            }
            read += 46
        }
    }

    private fun buildHierarchy() {
        for (i in 0 until instruments.size - 1) {
            val inst = instruments[i]
            val nextInst = instruments[i + 1]

            for (b in inst.bagNdx until nextInst.bagNdx) {
                if (b >= rawIbags.size - 1) break
                val currentBag = rawIbags[b]
                val nextBag = rawIbags[b + 1]

                for (g in currentBag.genNdx until nextBag.genNdx) {
                    val gen = rawIgens[g]
                    if (gen.code == 53) {
                        val sampleIdx = gen.amount.toInt()
                        if (sampleIdx in samples.indices) {
                            inst.samples.add(samples[sampleIdx])
                        }
                    }
                }
            }
        }

        for (i in 0 until presets.size - 1) {
            val preset = presets[i]
            val nextPreset = presets[i + 1]

            for (b in preset.bagNdx until nextPreset.bagNdx) {
                if (b >= rawPbags.size - 1) break
                val currentBag = rawPbags[b]
                val nextBag = rawPbags[b + 1]

                for (g in currentBag.genNdx until nextBag.genNdx) {
                    val gen = rawPgens[g]
                    if (gen.code == 41) {
                        val instIdx = gen.amount.toInt()
                        if (instIdx in instruments.indices) {
                            preset.instruments.add(instruments[instIdx])
                        }
                    }
                }
            }
        }
    }

    data class Preset(
        val name: String,
        val presetID: Int,
        val bank: Int,
        internal val bagNdx: Int,
        val instruments: MutableList<Instrument> = mutableListOf()
    )

    data class Instrument(
        val name: String,
        internal val bagNdx: Int,
        val samples: MutableList<SampleHeader> = mutableListOf()
    )

    data class SampleHeader(
        val name: String, val start: Int, val end: Int, val startLoop: Int, val endLoop: Int,
        val sampleRate: Int, val originalPitch: Int, val pitchCorrection: Int, val sampleType: Int
    )

    private data class RawBag(val genNdx: Int, val modNdx: Int)
    private data class RawGenMod(val code: Int, val amount: Short)

    fun createInstrument(presetIndex: Int): musikt.Instrument {
        require(presetIndex in presets.indices) { "Preset index out of bounds" }
        return SF2Instrument(presets[presetIndex], sampleData)
    }

    operator fun get(presetIndex: Int) = createInstrument(presetIndex)
    operator fun get(preset: String) = createInstrument(presets.indexOfFirst { it.name.trim()
        .equals(preset.trim(), ignoreCase = true) }
        .let { index -> if (index != -1) index else throw IndexOutOfBoundsException("No such preset: $preset") })
}


class SF2Instrument(
    private val preset: SoundFont2.Preset,
    private val sampleData: ShortArray
) : Instrument {

    override fun voice(frequency: Sequence<Double>, velocity: Double, sampleRate: Int): Voice {
        val freqIterator = frequency.iterator()
        if (!freqIterator.hasNext()) {
            return SilentVoice
        }

        val initialFreq = freqIterator.next()

        var matchedSample: SoundFont2.SampleHeader? = null

        for (inst in preset.instruments) {
            for (sample in inst.samples) {
                matchedSample = sample
                break
            }
            if (matchedSample != null) break
        }

        val sample = matchedSample ?: return SilentVoice
        return SF2Voice(sample, sampleData, freqIterator, initialFreq, sampleRate)
    }
}

class SF2Voice(
    private val sample: SoundFont2.SampleHeader,
    private val sampleData: ShortArray,
    override val frequency: Iterator<Double>,
    initialFreq: Double,
    private val outputSampleRate: Int
) : Voice {

    private var currentPlaybackIndex = sample.start.toDouble()
    private var isReleased = false

    private val rootFrequency = 440.0 * 2.0.pow((sample.originalPitch - 69.0) / 12.0)
    private var currentFrequency = initialFreq

    override fun hasNext(): Boolean {
        return !(!isLooping() && currentPlaybackIndex >= sample.end)
    }

    override fun next(): Double {
        if (!hasNext()) return 0.0

        if (frequency.hasNext()) {
            currentFrequency = frequency.next()
        }

        val indexFloor = currentPlaybackIndex.toInt()
        val indexCeil = if (indexFloor + 1 >= sample.end) indexFloor else indexFloor + 1

        val fraction = currentPlaybackIndex - indexFloor

        val s0 = sampleData.getOrElse(indexFloor) { 0.toShort() }.toDouble() / 32768.0
        val s1 = sampleData.getOrElse(indexCeil) { 0.toShort() }.toDouble() / 32768.0
        val interpolatedSample = s0 + fraction * (s1 - s0)

        val pitchRatio = currentFrequency / rootFrequency
        val playbackSpeed = (sample.sampleRate.toDouble() / outputSampleRate) * pitchRatio
        currentPlaybackIndex += playbackSpeed

        if (isLooping() && currentPlaybackIndex >= sample.endLoop) {
            val loopLength = sample.endLoop - sample.startLoop
            if (loopLength > 0) {
                currentPlaybackIndex -= loopLength
            }
        }

        return interpolatedSample
    }

    override fun release() {
        isReleased = true
    }

    private fun isLooping(): Boolean {
        return (sample.sampleType == 1) && !isReleased
    }
}