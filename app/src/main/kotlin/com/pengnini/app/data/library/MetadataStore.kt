package com.pengnini.app.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 폴더별 사이드카(`.pengnini.json`)에 사용자 메타데이터(rating/favorite/tags)를 기록·복원한다.
 *
 * DB(video_user_data)가 원본이고 사이드카는 이식·영속용 best-effort 백업이다.
 * - 읽기 실패/JSON 손상: 무시하고 빈 맵 반환(앱은 DB로 동작).
 * - 쓰기: 임시파일에 먼저 쓴 뒤 교체. 읽기전용 폴더 등 실패는 조용히 무시(DB가 보존).
 * - 키는 영상 title(확장자 제외 파일명) — 스크립트 매칭과 동일하게 트리 내 고유 가정.
 */
class MetadataStore(private val context: Context) {

    data class Entry(
        val rating: Int,
        val favorite: Boolean,
        val tags: String,
        val customTitle: String? = null,
    )

    fun read(folderUri: String): Map<String, Entry> = runCatching {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return emptyMap()
        val file = tree.findFile(SIDECAR_NAME)?.takeIf { it.isFile } ?: return emptyMap()
        val text = context.contentResolver.openInputStream(file.uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return emptyMap()
        Json.parseToJsonElement(text).jsonObject.mapValues { (_, v) ->
            val o = v.jsonObject
            Entry(
                rating = o["rating"]?.jsonPrimitive?.intOrNull ?: 0,
                favorite = o["favorite"]?.jsonPrimitive?.booleanOrNull ?: false,
                tags = o["tags"]?.jsonPrimitive?.contentOrNull ?: "",
                customTitle = o["customTitle"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }.getOrDefault(emptyMap())

    fun write(folderUri: String, entries: Map<String, Entry>) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return
            val json = buildJsonObject {
                entries.forEach { (title, e) ->
                    putJsonObject(title) {
                        put("rating", e.rating)
                        put("favorite", e.favorite)
                        put("tags", e.tags)
                        e.customTitle?.let { put("customTitle", it) }
                    }
                }
            }.toString()

            // 임시파일에 먼저 기록 → 기존 파일 교체 (쓰기 중단 시 손상 구간 최소화)
            tree.findFile(TMP_NAME)?.delete()
            val tmp = tree.createFile(MIME, TMP_NAME) ?: return
            val ok = context.contentResolver.openOutputStream(tmp.uri, "wt")?.use {
                it.write(json.toByteArray(Charsets.UTF_8)); true
            } ?: false
            if (!ok) { tmp.delete(); return }
            tree.findFile(SIDECAR_NAME)?.delete()
            tmp.renameTo(SIDECAR_NAME)
        }
    }

    /** 폴더의 사이드카(.pengnini.json)를 삭제 — 라이브러리 완전 초기화용. */
    fun delete(folderUri: String) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return
            tree.findFile(SIDECAR_NAME)?.delete()
            tree.findFile(TMP_NAME)?.delete()
        }
    }

    private companion object {
        const val SIDECAR_NAME = ".pengnini.json"
        const val TMP_NAME = ".pengnini.tmp"
        const val MIME = "application/json"
    }
}
