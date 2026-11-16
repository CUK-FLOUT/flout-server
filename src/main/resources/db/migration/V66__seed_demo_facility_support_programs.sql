-- FLOUT demo data: facility support programs used by the end-to-end demonstration flow.
-- These records are not real public notices. Keep the demo identity visible in every environment.
-- The three programs cover every facility recognized by SupportFacilityMatcher.

-- Entrance and window flood barriers
INSERT INTO support_programs (
    external_code,
    name,
    description,
    target_description,
    operating_status,
    eligibility_conditions,
    exclusion_conditions,
    supported_facilities,
    required_documents,
    application_method,
    application_status,
    application_start_date,
    application_end_date,
    official_source,
    official_url,
    program_kind,
    source_ids,
    source_verified_at,
    source_metadata,
    created_at
)
SELECT
    'DEMO-FACILITY-BARRIER-2026',
    '[시연용] 출입구·창호 침수방지시설 설치 지원',
    '출입구와 창호를 통한 빗물 유입을 줄이기 위한 침수방지시설 설치를 지원하는 FLOUT 시연용 사업입니다.',
    '현장점검에서 출입구 또는 창호 침수방지시설 설치를 권장받은 부천시 거주 가구',
    '시연 기간 접수중',
    'FLOUT 현장점검 결과에서 지원시설 설치가 권장되고 신청 주소가 부천시인 경우',
    '동일 시설에 대해 이미 지원을 완료한 경우 제외',
    '["물막이판", "차수판", "자동차수시설", "방수문", "수밀문"]',
    '지원 신청서, 현장점검 결과서, 신청인 확인서류',
    'FLOUT 주민 서비스에서 권장시설을 선택하여 온라인 신청',
    'OPEN',
    '2026-01-01',
    '2026-12-31',
    'FLOUT 시연 데이터',
    NULL,
    'FACILITY',
    '["DEMO"]',
    '2026-11-16',
    '{"demo":true,"category":"BARRIER","region":"경기도 부천시"}',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM support_programs WHERE external_code = 'DEMO-FACILITY-BARRIER-2026'
);

-- Drainage and backflow prevention facilities
INSERT INTO support_programs (
    external_code,
    name,
    description,
    target_description,
    operating_status,
    eligibility_conditions,
    exclusion_conditions,
    supported_facilities,
    required_documents,
    application_method,
    application_status,
    application_start_date,
    application_end_date,
    official_source,
    official_url,
    program_kind,
    source_ids,
    source_verified_at,
    source_metadata,
    created_at
)
SELECT
    'DEMO-FACILITY-DRAINAGE-2026',
    '[시연용] 배수·역류 방지시설 설치 지원',
    '저지대 주택의 배수 능력을 보완하고 하수 역류를 방지하는 시설 설치를 지원하는 FLOUT 시연용 사업입니다.',
    '현장점검에서 배수 또는 역류 방지시설 설치를 권장받은 부천시 거주 가구',
    '시연 기간 접수중',
    'FLOUT 현장점검 결과에서 지원시설 설치가 권장되고 신청 주소가 부천시인 경우',
    '동일 시설에 대해 이미 지원을 완료한 경우 제외',
    '["역류방지밸브", "배수펌프", "수중펌프", "집수정", "배수로", "배수구", "우수받이"]',
    '지원 신청서, 현장점검 결과서, 신청인 확인서류',
    'FLOUT 주민 서비스에서 권장시설을 선택하여 온라인 신청',
    'OPEN',
    '2026-01-01',
    '2026-12-31',
    'FLOUT 시연 데이터',
    NULL,
    'FACILITY',
    '["DEMO"]',
    '2026-11-16',
    '{"demo":true,"category":"DRAINAGE","region":"경기도 부천시"}',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM support_programs WHERE external_code = 'DEMO-FACILITY-DRAINAGE-2026'
);

-- Flood detection and warning facilities
INSERT INTO support_programs (
    external_code,
    name,
    description,
    target_description,
    operating_status,
    eligibility_conditions,
    exclusion_conditions,
    supported_facilities,
    required_documents,
    application_method,
    application_status,
    application_start_date,
    application_end_date,
    official_source,
    official_url,
    program_kind,
    source_ids,
    source_verified_at,
    source_metadata,
    created_at
)
SELECT
    'DEMO-FACILITY-ALERT-2026',
    '[시연용] 침수 감지·경보시설 설치 지원',
    '침수 징후를 조기에 확인할 수 있는 감지·경보시설 설치를 지원하는 FLOUT 시연용 사업입니다.',
    '현장점검에서 침수 감지 또는 경보시설 설치를 권장받은 부천시 거주 가구',
    '시연 기간 접수중',
    'FLOUT 현장점검 결과에서 지원시설 설치가 권장되고 신청 주소가 부천시인 경우',
    '동일 시설에 대해 이미 지원을 완료한 경우 제외',
    '["침수감지장치", "침수경보장치"]',
    '지원 신청서, 현장점검 결과서, 신청인 확인서류',
    'FLOUT 주민 서비스에서 권장시설을 선택하여 온라인 신청',
    'OPEN',
    '2026-01-01',
    '2026-12-31',
    'FLOUT 시연 데이터',
    NULL,
    'FACILITY',
    '["DEMO"]',
    '2026-11-16',
    '{"demo":true,"category":"ALERT","region":"경기도 부천시"}',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM support_programs WHERE external_code = 'DEMO-FACILITY-ALERT-2026'
);
