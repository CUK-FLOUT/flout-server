# 점검 결과의 권장 예방조치

임시저장 `PUT /api/admin/inspections/{inspectionId}/result/draft`와 저장·공개 `POST /api/admin/inspections/{inspectionId}/result`는 같은 권장 예방조치 구조를 사용합니다.

```json
{
  "recommendedActions": [
    { "description": "출입구 물막이판 설치" },
    { "description": "배수구 정비" },
    { "description": "사전점검 및 미리 정비" }
  ]
}
```

위 예시는 해당 필드만 발췌했으며 공개 요청에는 기존 필수 항목도 함께 전달합니다.
각 항목은 `description`만 받으며 `title`, `relatedTarget`은 사용하지 않습니다.
취약지점과 일대일로 매칭하지 않으므로 취약지점 하나에 여러 조치를 추가할 수 있습니다.
배열 순서대로 저장하며 매 저장 시 전체 목록을 교체합니다. 기존 제한은 최대 20건, 항목당 4000자입니다.
임시저장은 빈 설명과 미완성 항목을 허용하고 목록 생략·null은 빈 목록으로 저장합니다.
공개는 하나 이상의 조치와 각 항목의 공백이 아닌 설명이 필수입니다.
두 API의 응답은 기존 저장 상태·시각 정보로 유지하며 조치 목록을 새로 반환하지 않습니다.
기존 DB의 title·related_target 컬럼은 과거 데이터 보존을 위해 삭제하지 않고, 신규 저장에는 사용하지 않습니다.
