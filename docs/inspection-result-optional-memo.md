# 점검 결과 내부 메모 선택 입력

`POST /api/admin/inspections/{inspectionId}/result`의 `internalMemo`는 선택 입력입니다.
필드 생략 또는 null은 DB에 null로 저장하며 빈 문자열·공백도 허용합니다.
문자열을 입력하면 최대 4000자까지 허용하고, 초과 시 400을 반환합니다.
내부 메모는 주민에게 공개하지 않습니다.

이 변경은 점검 결과의 메모에 적용됩니다. 별도 신청 내부 메모 API의 요청 규칙은 유지합니다.
