package com.pengnini.app.data.smb

import android.media.MediaDataSource

/** MediaMetadataRetriever가 SMB 파일을 읽을 수 있게 하는 어댑터(썸네일·메타 추출용). */
class SmbMediaDataSource(smb: SmbManager, fileUri: String) : MediaDataSource() {
    private val file = smb.openFile(fileUri)
    private val sizeBytes = try {
        file.fileInformation.standardInformation.endOfFile
    } catch (e: Exception) {
        runCatching { file.close() } // 크기 조회 실패 시 File 핸들 누수 방지
        throw e
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= sizeBytes) return -1
        return file.read(buffer, position, offset, size)
    }

    override fun getSize(): Long = sizeBytes

    override fun close() {
        runCatching { file.close() }
    }
}
