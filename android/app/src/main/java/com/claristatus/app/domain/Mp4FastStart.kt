package com.claristatus.app.domain

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room, 100% offline utility to relocate the MP4 'moov' atom to the front
 * of the file before 'mdat' (equivalent to ffmpeg's -movflags +faststart).
 *
 * WhatsApp Status strictly requires faststart (moov at the front) to enable
 * streaming playback; without it, WhatsApp forces a secondary 720p transcode.
 */
object Mp4FastStart {

    private const val TAG = "Mp4FastStart"

    private fun log(msg: String) {
        println("[$TAG] $msg")
    }

    private const val BOX_FTYP = 0x66747970 // "ftyp"
    private const val BOX_MOOV = 0x6D6F6F76 // "moov"
    private const val BOX_MDAT = 0x6D646174 // "mdat"
    private const val BOX_STCO = 0x7374636F // "stco"
    private const val BOX_CO64 = 0x636F3634 // "co64"

    /**
     * Inspects the input MP4 file. If the 'moov' atom is located after 'mdat',
     * shifts 'moov' to the front, adjusts chunk offsets (stco/co64), and rewrites
     * the file in-place.
     *
     * @param mp4File The video file to process.
     * @return true if faststart was applied or already present, false on failure.
     */
    fun process(mp4File: File): Boolean {
        if (!mp4File.exists() || mp4File.length() < 32) {
            return false
        }

        val tempFile = File(mp4File.parentFile, "${mp4File.name}.faststart.tmp")
        try {
            RandomAccessFile(mp4File, "r").use { raf ->
                var ftypOffset = -1L
                var ftypSize = 0L
                var moovOffset = -1L
                var moovSize = 0L
                var mdatOffset = -1L
                var mdatSize = 0L

                var fileOffset = 0L
                val fileLength = raf.length()

                val headerBuf = ByteArray(8)
                while (fileOffset < fileLength) {
                    raf.seek(fileOffset)
                    if (raf.read(headerBuf) < 8) break

                    val buffer = ByteBuffer.wrap(headerBuf).order(ByteOrder.BIG_ENDIAN)
                    var boxSize = buffer.int.toLong() and 0xFFFFFFFFL
                    val boxType = buffer.int

                    var headerLen = 8L
                    if (boxSize == 1L) {
                        // 64-bit large box size
                        val largeBuf = ByteArray(8)
                        if (raf.read(largeBuf) < 8) break
                        boxSize = ByteBuffer.wrap(largeBuf).order(ByteOrder.BIG_ENDIAN).long
                        headerLen = 16L
                    } else if (boxSize == 0L) {
                        boxSize = fileLength - fileOffset
                    }

                    if (boxSize < headerLen) break

                    when (boxType) {
                        BOX_FTYP -> {
                            ftypOffset = fileOffset
                            ftypSize = boxSize
                        }
                        BOX_MOOV -> {
                            moovOffset = fileOffset
                            moovSize = boxSize
                        }
                        BOX_MDAT -> {
                            mdatOffset = fileOffset
                            mdatSize = boxSize
                        }
                    }

                    fileOffset += boxSize
                }

                // If moov is already before mdat, the file is already faststart-compliant
                if (moovOffset in 0 until mdatOffset) {
                    log("File ${mp4File.name} is already faststart (+faststart)")
                    return true
                }

                if (moovOffset < 0 || mdatOffset < 0 || moovSize <= 0) {
                    log("Could not find valid moov and mdat boxes in ${mp4File.name}")
                    return false
                }

                // Read entire moov box into memory (typically 20KB - 100KB)
                if (moovSize > 25 * 1024 * 1024) {
                    log("moov atom too large: $moovSize bytes")
                    return false
                }

                val moovBytes = ByteArray(moovSize.toInt())
                raf.seek(moovOffset)
                raf.readFully(moovBytes)

                // Patch chunk offsets inside moovBytes: shift by moovSize
                patchChunkOffsets(moovBytes, moovSize)

                // Write out new file: ftyp -> modified moov -> mdat -> any other remaining atoms
                FileOutputStream(tempFile).use { fos ->
                    // 1. Copy ftyp box
                    if (ftypSize > 0) {
                        val ftypBytes = ByteArray(ftypSize.toInt())
                        raf.seek(ftypOffset)
                        raf.readFully(ftypBytes)
                        fos.write(ftypBytes)
                    }

                    // 2. Write relocated moov box
                    fos.write(moovBytes)

                    // 3. Copy mdat and any trailing data before moov
                    raf.seek(mdatOffset)
                    val copyBuffer = ByteArray(64 * 1024)
                    var bytesToCopy = moovOffset - mdatOffset
                    while (bytesToCopy > 0) {
                        val readSize = minOf(bytesToCopy, copyBuffer.size.toLong()).toInt()
                        val bytesRead = raf.read(copyBuffer, 0, readSize)
                        if (bytesRead <= 0) break
                        fos.write(copyBuffer, 0, bytesRead)
                        bytesToCopy -= bytesRead
                    }
                }
            }

            // raf is now closed! Replace original file with faststart temp file
            if (tempFile.length() > 0) {
                mp4File.delete()
                val renamed = tempFile.renameTo(mp4File)
                log("Successfully converted ${mp4File.name} to faststart (moov at front)")
                return renamed
            } else {
                tempFile.delete()
                return false
            }
        } catch (e: Exception) {
            tempFile.delete()
            log("Failed to apply faststart to ${mp4File.name}: ${e.message}")
            return false
        }
    }

    /**
     * Recursively or linearly scans the moov atom buffer and updates 'stco' (32-bit)
     * and 'co64' (64-bit) chunk offset tables by adding the moov atom size.
     */
    private fun patchChunkOffsets(moovBytes: ByteArray, shiftAmount: Long) {
        val buffer = ByteBuffer.wrap(moovBytes).order(ByteOrder.BIG_ENDIAN)
        val length = moovBytes.size

        var pos = 0
        while (pos + 8 <= length) {
            val boxSize = buffer.getInt(pos).toLong() and 0xFFFFFFFFL
            val boxType = buffer.getInt(pos + 4)

            if (boxType == BOX_STCO && pos + 16 <= length) {
                // stco: 4 size, 4 type, 1 version, 3 flags, 4 entryCount, then entryCount * 4 offsets
                val entryCount = buffer.getInt(pos + 12) and 0x7FFFFFFF
                if (pos + 16 + entryCount * 4 <= length) {
                    for (i in 0 until entryCount) {
                        val offsetPos = pos + 16 + i * 4
                        val currentOffset = buffer.getInt(offsetPos).toLong() and 0xFFFFFFFFL
                        val newOffset = currentOffset + shiftAmount
                        buffer.putInt(offsetPos, newOffset.toInt())
                    }
                }
            } else if (boxType == BOX_CO64 && pos + 16 <= length) {
                // co64: 4 size, 4 type, 1 version, 3 flags, 4 entryCount, then entryCount * 8 offsets
                val entryCount = buffer.getInt(pos + 12) and 0x7FFFFFFF
                if (pos + 16 + entryCount * 8 <= length) {
                    for (i in 0 until entryCount) {
                        val offsetPos = pos + 16 + i * 8
                        val currentOffset = buffer.getLong(offsetPos)
                        val newOffset = currentOffset + shiftAmount
                        buffer.putLong(offsetPos, newOffset)
                    }
                }
            }

            pos++
        }
    }
}
