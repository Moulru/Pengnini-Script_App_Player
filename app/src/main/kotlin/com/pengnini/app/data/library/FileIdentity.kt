package com.pengnini.app.data.library

import android.net.Uri
import android.provider.DocumentsContract
import com.pengnini.app.data.smb.SmbManager

/**
 * 같은 파일을 가리키는 서로 다른 URI를 하나로 묶는 키.
 *
 * SAF 문서 URI는 어느 트리를 거쳐 얻었는지에 따라 문자열이 달라진다
 * (`tree/primary:A/document/primary:A/b/x.mp4` 와 `tree/primary:A/b/document/primary:A/b/x.mp4`).
 * 실제 파일을 가리키는 건 provider(authority) + 문서 ID다.
 * SMB는 host·경로 모두 대소문자를 구분하지 않는다.
 */
internal fun fileIdentity(uri: String): String {
    val u = Uri.parse(uri)
    if (SmbManager.isSmb(uri)) {
        return "smb://" + u.host.orEmpty().lowercase() + "/" + u.pathSegments.joinToString("/").lowercase()
    }
    val docId = runCatching { DocumentsContract.getDocumentId(u) }.getOrNull() ?: return uri
    return "${u.authority}|$docId"
}

/** 등록 폴더(SAF 트리 URI 또는 SMB 폴더 URI)의 [fileIdentity]. */
internal fun folderIdentity(uri: String): String {
    if (SmbManager.isSmb(uri)) return fileIdentity(uri)
    val u = Uri.parse(uri)
    val treeId = runCatching { DocumentsContract.getTreeDocumentId(u) }.getOrNull() ?: return uri
    return "${u.authority}|$treeId"
}

/** [childUri]가 등록 폴더 [folderUri]를 거쳐 스캔으로 얻은 URI인지. 수동 연결(파일 선택기)은 여기 해당하지 않는다. */
internal fun isScannedUnder(childUri: String, folderUri: String): Boolean =
    childUri.startsWith(folderUri.trimEnd('/') + "/", ignoreCase = true)
