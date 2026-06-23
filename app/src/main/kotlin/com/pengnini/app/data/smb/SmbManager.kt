package com.pengnini.app.data.smb

import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.pengnini.app.data.secure.SmbCredentialStore
import java.util.EnumSet

/**
 * SMB(2/3) 연결·세션·공유를 host 단위로 캐시하고 디렉토리 목록·파일 읽기를 제공한다.
 * 모든 호출은 네트워크 블로킹이므로 IO 스레드에서 이뤄져야 한다.
 */
class SmbManager(private val credentials: SmbCredentialStore) {

    data class SmbEntry(val name: String, val isDirectory: Boolean, val size: Long)

    data class SmbPath(val host: String, val share: String, val path: String) {
        /** 공유 내부 경로(역슬래시 구분, smbj 규약). 루트면 빈 문자열. */
        val sharePath: String get() = path.trim('/').replace('/', '\\')
    }

    private val client = SMBClient()
    private val connections = HashMap<String, Connection>()
    private val sessions = HashMap<String, Session>()
    private val shares = HashMap<String, DiskShare>()

    /**
     * 같은 (host/share)의 DiskShare를 재사용하고, 끊겼으면 재연결한다.
     * connection이 죽었으면 그 host의 session·share 캐시까지 함께 폐기하고(stale 재사용 방지) 새로 연결한다.
     */
    @Synchronized
    private fun diskShare(p: SmbPath): DiskShare {
        val shareKey = "${p.host}/${p.share}".lowercase()
        shares[shareKey]?.let {
            if (it.isConnected) return it
            shares.remove(shareKey)
        }

        val conn: Connection = connections[p.host]?.takeIf { it.isConnected }
            ?: run {
                // 죽은 connection과 그에 묶인 session·share를 모두 폐기한 뒤 재연결
                connections[p.host]?.let { old -> runCatching { old.close() } }
                sessions.remove(p.host)
                val prefix = "${p.host.lowercase()}/"
                shares.keys.filter { it.startsWith(prefix) }.toList().forEach { shares.remove(it) }
                client.connect(p.host).also { connections[p.host] = it }
            }

        val session: Session = sessions[p.host]
            ?: run {
                val cred = credentials.get(p.host)
                val auth = if (cred == null || cred.username.isBlank()) {
                    AuthenticationContext.guest()
                } else {
                    AuthenticationContext(cred.username, cred.password.toCharArray(), cred.domain)
                }
                conn.authenticate(auth).also { sessions[p.host] = it }
            }

        val share = session.connectShare(p.share) as DiskShare
        shares[shareKey] = share
        return share
    }

    fun list(folderUri: String): List<SmbEntry> {
        val p = parse(folderUri)
        return diskShare(p).list(p.sharePath)
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info ->
                val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                SmbEntry(info.fileName, isDir, info.endOfFile)
            }
    }

    /** 랜덤 액세스용 File 핸들(ExoPlayer DataSource에서 사용). 사용 후 close 필수. */
    fun openFile(fileUri: String): File {
        val p = parse(fileUri)
        return diskShare(p).openFile(
            p.sharePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
    }

    /** 작은 파일(스크립트) 통째 읽기 — File 핸들을 확실히 닫는다. */
    fun readBytes(fileUri: String): ByteArray {
        val file = openFile(fileUri)
        return try {
            file.inputStream.use { it.readBytes() }
        } finally {
            runCatching { file.close() }
        }
    }

    @Synchronized
    fun release() {
        runCatching { shares.values.forEach { it.close() } }
        runCatching { sessions.values.forEach { it.close() } }
        runCatching { connections.values.forEach { it.close() } }
        shares.clear()
        sessions.clear()
        connections.clear()
    }

    companion object {
        fun isSmb(uri: String): Boolean = uri.startsWith("smb://", ignoreCase = true)

        fun parse(uri: String): SmbPath {
            val u = Uri.parse(uri)
            val host = u.host ?: error("SMB host 없음: $uri")
            val segs = u.pathSegments
            require(segs.isNotEmpty()) { "SMB share 없음: $uri" }
            return SmbPath(host, segs.first(), segs.drop(1).joinToString("/"))
        }

        /** host/share/path 조각으로 표준 smb URI 생성(세그먼트별 퍼센트 인코딩 → parse와 일관). */
        fun buildUri(host: String, share: String, path: String): String {
            // 사용자가 smb:// 접두사나 슬래시/역슬래시를 붙여 넣어도 host만 깔끔히 추출
            val cleanHost = host.trim().substringAfter("://")
                .trim('/', '\\').substringBefore('/').substringBefore('\\').trim()
            val segs = (listOf(share) + path.split('/', '\\')).filter { it.isNotBlank() }
            return "smb://" + cleanHost + "/" + segs.joinToString("/") { Uri.encode(it) }
        }

        /** 폴더 uri 아래 자식 이름을 붙여 자식 uri 생성(이름 인코딩). */
        fun childUri(folderUri: String, name: String): String =
            folderUri.trimEnd('/') + "/" + Uri.encode(name)
    }
}
