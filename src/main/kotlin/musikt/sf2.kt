package musikt

import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundFont2(val file: File) {
    lateinit var name: String private set

    init {
        val ist = file.inputStream().buffered()
        readChunk(ist)
    }

    private fun readChunk(ist: BufferedInputStream): Int {
        val magicType = ist.readNBytes(4).decodeToString()
        val length = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
        var consumed = 8 + length
        when (magicType) {
            "LIST", "RIFF" -> parseList(ist, length)
            "INAM" -> parseINAM(ist, length)
            "phdr" -> parsePhdr(ist, length)
            else -> {
                println("SF2: $magicType:$length")
                require(length >= 0) {"$magicType tried using negative length"}
                ist.skipNBytes(length.toLong())
                println("SF2: /$magicType:$length")
            }
        }
        if(length % 2 == 1) {
            println("SF2: $magicType:$length is odd, skipping pad")
            ist.skipNBytes(1)
            consumed++
        }
        return consumed
    }

    private fun parsePhdr(ist: BufferedInputStream, length: Int) {
        println("SF2: phdr")
        var read = 0
        while(read < length) {
            parsePhdrPreset(ist)
            read += 38
        }
    }

    private fun parsePhdrPreset(ist: BufferedInputStream) {
        val presetName = ist.readNBytes(20).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
        val presetN = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val presetBank = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val presetBagNdx = ByteBuffer.wrap(ist.readNBytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val library = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
        val genre = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
        val morph = ByteBuffer.wrap(ist.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
        println("SF2: $presetName($presetN, $presetBank, $presetBagNdx, $library, $genre, $morph)")
    }

    private fun parseINAM(ist: BufferedInputStream, length: Int) {
        val name = ist.readNBytes(length).decodeToString()
        println("SF2: INAM: $name")
        this.name = name
    }

    private fun parseList(ist: BufferedInputStream, length: Int) {
        val listType = ist.readNBytes(4).decodeToString()
        println("SF2: LIST/RIFF: $listType $length")
        var read = 4
        while (read < length) {
            read += readChunk(ist)
        }
    }
}