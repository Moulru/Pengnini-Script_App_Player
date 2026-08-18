package com.pengnini.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 여백 단계. 중간값을 만들지 않는 것이 핵심 — 값 자체보다 "4·6·7·9가 섞이지 않는다"는
 * 사실이 정렬감을 만든다. 새 코드는 이 목록 밖의 dp를 쓰지 않는다.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** 라운드 3종. 작은 요소 / 카드·그룹 / 알약. */
object Radius {
    val small = 8.dp
    val card = 14.dp
    val pill = 999.dp
}

/**
 * 글자 크기. 단계를 5개로 줄이고 인접 간격을 벌려 위계가 한눈에 잡히게 한다.
 * 밀도는 지금과 비슷하게 유지 — 위계를 만든다고 전부 키우면 한 화면에 담기는 영상 수가 준다.
 */
object TextSize {
    /** 화면 제목. 화면당 하나뿐이라 밀도에 영향이 없다. */
    val screenTitle = 22.sp
    /** 목록 행 제목·본문. */
    val body = 14.sp
    /** 카드 제목. */
    val cardTitle = 13.sp
    /** 보조 설명·부제. */
    val support = 12.sp
    /** 메타·캡션. */
    val caption = 11.sp
}

/** 카드 안에서 고정으로 잡아 두는 크기 — 내용이 달라져도 카드 높이가 변하지 않게. */
object CardMetrics {
    /** 썸네일 가로세로비. 16:9보다 세로를 조금 키워 그림 면적을 넓힌다. */
    const val THUMB_ASPECT = 16f / 10f
    /** 별·하트 아이콘. */
    val metaIcon = 11.dp
    /** ⋮ 버튼 터치 영역. */
    val moreTouch = 32.dp
}
