
-- 부천시 침수방지시설 설치 지원사업 초기 데이터
-- 접수상태 및 세부 자격은 최신 공고 확인 필요

INSERT INTO support_programs (
    name,
    description,
    target_description,
    operating_status,
    supported_facilities,
    application_status,
    official_source,
    official_url,
    created_at
)
SELECT
    '부천시 침수방지시설 설치 지원',
    '집중호우 시 침수 피해를 줄이기 위한 물막이판, 역류방지밸브 등 침수방지시설 설치 지원',
    '부천시 침수이력 건물 및 공고별 지원 대상',
    '공고 확인 필요',
    '["물막이판", "역류방지밸브"]',
    'UNKNOWN',
    '부천시청',
    'https://www.bucheon.go.kr/site/program/board/basicboard/view?boardtypeid=26736&encid=85QbhhTuf7xE6PtEVOi5qw%3D%3D',
    CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
    SELECT 1
    FROM support_programs
    WHERE name = '부천시 침수방지시설 설치 지원'
);
