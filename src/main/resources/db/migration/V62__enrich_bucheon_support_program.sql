-- FLOUT-216: Enrich the V61 seed from Bucheon's published 2023 notice.
-- The source is HISTORICAL: do NOT interpret it as a currently open 2026 program.
-- Keep application_status = UNKNOWN and both application dates NULL.
-- Do not overwrite any previously populated, newer information.

UPDATE support_programs
SET
    eligibility_conditions = CASE
        WHEN eligibility_conditions IS NULL OR TRIM(eligibility_conditions) = '' THEN
            '2023년 부천시 공고 기준: 침수이력 건물(주택·소규모 상가) 및 소규모 공동주택(아파트). 침수이력 건물은 2022년 집중호우 시 침수피해로 재난지원금을 지급받은 가구로 안내되었습니다. 현재 지원대상은 최신 공고 확인이 필요합니다.'
        ELSE eligibility_conditions
    END,
    required_documents = CASE
        WHEN required_documents IS NULL OR TRIM(required_documents) = '' THEN
            '2023년 공고 기준: 침수방지시설 설치 신청서 및 동의서(현장조사 후 작성). 현재 필요서류는 담당부서에 확인해 주세요.'
        ELSE required_documents
    END,
    application_method = CASE
        WHEN application_method IS NULL OR TRIM(application_method) = '' THEN
            '2023년 공고 기준: 현장조사(설치 가능 여부 및 지원대상 확인) → 설치 신청서·동의서 작성 → 침수방지시설 설치. 2026년 접수 여부 및 신청방법은 부천시 공식 공고에서 확인해 주세요.'
        ELSE application_method
    END,
    official_source = CASE
        WHEN official_source IS NULL OR TRIM(official_source) IN ('', '부천시청') THEN
            '부천시청 2023년 재해취약주택 침수방지시설 설치지원 안내 (과거 공고)'
        ELSE official_source
    END,
    official_url = CASE
        WHEN official_url IS NULL OR TRIM(official_url) = '' OR
             official_url = 'https://www.bucheon.go.kr/site/program/board/basicboard/view?boardtypeid=26736&encid=85QbhhTuf7xE6PtEVOi5qw%3D%3D' THEN
            'https://www.bucheon.go.kr/site/program/board/basicboard/view?boardtypeid=26736&currentpage=1&encid=n3P1SGNnwfELJR+Dh1Ez8A%3D%3D&menuid=148004001001&pagesize=10'
        ELSE official_url
    END
WHERE name = '부천시 침수방지시설 설치 지원';
