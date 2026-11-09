-- 부천시 365안전센터의 침수방지시설 설치 지원 안내를 초기 매칭 데이터로 사용한다.
-- 출처: https://www.bucheon.go.kr/site/program/board/basicboard/view?boardtypeid=26736&encid=85QbhhTuf7xE6PtEVOi5qw%3D%3D
-- 모집 여부는 시기별 공고로 다시 확인해야 하므로 운영 상태를 OPEN으로 단정하지 않는다.
INSERT INTO support_programs (
    name,
    description,
    target_description,
    operating_status,
    created_at
)
SELECT
    '부천시 침수방지시설 설치 지원',
    '집중호우 시 침수 피해를 줄이기 위한 물막이판, 역류방지밸브 등 침수방지시설 설치를 지원합니다.',
    '부천시 침수이력 건물(주택·소규모 상가) 및 소규모 공동주택 등 공고별 지원 대상',
    '공고 확인 필요',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM support_programs
    WHERE name = '부천시 침수방지시설 설치 지원'
);
