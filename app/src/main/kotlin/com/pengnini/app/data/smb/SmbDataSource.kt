package com.pengnini.app.data.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.smbj.share.File
import java.io.EOFException
import java.io.IOException

/**
 * SMB 파일을 ExoPlayer가 스트리밍 재생할 수 있게 하는 DataSource.
 * smbj File의 오프셋 지정 read로 random access(seek)를 지원한다.
 */
@UnstableApi
class SmbDataSource(private val smb: SmbManager) : BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var file: File? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)
        try {
            val f = smb.openFile(dataSpec.uri.toString())
            file = f
            val size = f.fileInformation.standardInformation.endOfFile
            position = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                size - dataSpec.position
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("SMB open 실패: ${dataSpec.uri}", e)
        }
        if (bytesRemaining < 0) throw EOFException()
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val f = file ?: throw IllegalStateException("SmbDataSource not opened")
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val read = try {
            f.read(buffer, position, offset, toRead)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("SMB read 실패", e)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        position += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        dataSpec = null
        try {
            file?.close()
        } catch (_: Exception) {
            // 닫기 실패는 무시
        } finally {
            file = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val smb: SmbManager) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(smb)
    }
}
