package com.pengnini.app.data.library

import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.bucketKey
import com.pengnini.app.data.smb.SmbManager

/**
 * 폴더 분류 한 칸. 등록한 폴더가 아니라 **영상이 실제로 들어 있는 하위 폴더** 단위다.
 * `Movies` 하나만 등록해도 `Movies/`, `Movies/여행`, `Movies/작업` 으로 나뉜다.
 *
 * @param key `VideoEntity.bucketKey` — 폴더별 조회·필터의 기준
 * @param label 표시 이름. 루트 바로 아래는 `Movies/`, 하위 폴더는 `Movies/여행`
 * @param count 그 폴더에 **직접** 든 영상 수(하위 폴더는 세지 않음)
 */
data class FolderBucket(
    val key: String,
    val label: String,
    val count: Int,
    val isNetwork: Boolean,
    val rootUri: String,
    val lastScannedAt: Long?,
)

/** 영상 목록과 등록 폴더 목록으로 폴더 분류를 만든다. 라벨 순 정렬. */
fun buildFolderBuckets(
    videos: List<VideoEntity>,
    roots: List<FolderEntity>,
): List<FolderBucket> {
    val rootByUri = roots.associateBy { it.uri }
    return videos.groupBy { it.bucketKey }
        .map { (key, group) ->
            val head = group.first()
            val root = rootByUri[head.folderUri]
            val rootLabel = root?.displayName ?: shortFolderName(head.folderUri)
            val path = head.bucketPath.orEmpty()
            FolderBucket(
                key = key,
                label = if (path.isBlank()) "$rootLabel/" else "$rootLabel/$path",
                count = group.size,
                isNetwork = SmbManager.isSmb(key),
                rootUri = head.folderUri,
                lastScannedAt = root?.lastScannedAt,
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** 표시 이름이 없는 폴더 URI에서 사람이 읽을 마지막 조각만 뽑는다. */
fun shortFolderName(uri: String): String {
    val decoded = runCatching { java.net.URLDecoder.decode(uri, "UTF-8") }.getOrDefault(uri)
    return decoded.substringAfterLast(':').substringAfterLast('/').ifBlank { decoded.takeLast(20) }
}
