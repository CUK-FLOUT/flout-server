-- FLOUT-216: 2026 reference records from programs.seed.json (as_of_date=2026-11-08).
-- Both are information / consultation references, not confirmed OPEN applications.
-- Facilities installation flow MUST NOT automatically treat RELOCATION/INSURANCE as eligible.
-- Unknown dates stay NULL; unknown documents stay NULL, never "no documents".
-- MySQL / H2 compatible INSERT ... SELECT ... WHERE NOT EXISTS.

-- BC-HOUSING-UPGRADE-2026 / RELOCATION
INSERT INTO support_programs (external_code, name, description, target_description, operating_status, eligibility_conditions, supported_facilities, required_documents, application_method, application_status, application_start_date, application_end_date, official_source, official_url, program_kind, source_ids, source_verified_at, source_metadata, created_at)
SELECT 'BC-HOUSING-UPGRADE-2026',
       '주거취약계층 주거상향 지원사업',
       '재해 우려 지하층 등 주거취약계층의 공공임대주택 이주와 정착을 지원.',
       '경기도 부천시 (세부 지원 자격 확인 필요)',
       '문의·접수 여부 확인 필요',
       '지정된 열악한 주거환경에서 3개월 이상 거주하는 사람 등.
최저주거기준 미달 지하층 또는 홍수·호우 등으로 이주가 필요하다고 시장 등이 인정한 지하층 포함.
소득·자산·주택소유 여부 등 자격 검증 필요.',
       '[]',
       NULL,
       '부천시 주거복지센터 상담 후 접수 안내. 유선·내방·방문·온라인 상담 안내가 있음.',
       'NEEDS_CONFIRMATION',
       NULL,
       NULL,
       '부천시·경기도 공식 안내',
       'https://bucheon.go.kr/site/homepage/menu/viewMenu?menuid=148006005023004',
       'RELOCATION',
       '["S2","S3"]',
       '2026-11-08',
       '{"year":2026,"kind":"RELOCATION","region":"경기도 부천시","action_tags":["RELOCATION"],"support_details":"주거상담, 주택 물색, 이사 지원, 필요 시 임시거처, 입주 후 복지 연계. 개인별 현금지원액은 이 자료에서 확정하지 않음.","service_period_end":null,"documents_complete":false,"process":["상담","신청서류 준비·접수","자격 검증","선정결과 통보","주택 물색·계약","이주·정착 지원"],"contact":{"department":"부천시 주거복지센터","phone":"032-340-0829","email":"housing@best.or.kr","address":"부천시 원미구 소사로 482, 부천종합운동장 1층"},"limitations":["2026년 안내 확인. 현재 잔여 물량·접수 가능 여부·구비서류는 센터 확인 필요.","시설 설치 신청 흐름에 연결하지 말고 이주 상담으로 안내.","기재되지 않은 이사비·보증금 금액을 생성하지 말 것."],"sources":{"S2":{"title":"주거취약계층 주거상향 지원사업","publisher":"부천시","document_date":"2026-01-26","url":"https://bucheon.go.kr/site/homepage/menu/viewMenu?menuid=148006005023004"},"S3":{"title":"주거취약계층 주거상향 지원사업","publisher":"경기도","document_date":null,"url":"https://www.gg.go.kr/contents/contents.do?ciIdx=987219&menuId=1534038"}}}',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM support_programs WHERE external_code = 'BC-HOUSING-UPGRADE-2026' OR name = '주거취약계층 주거상향 지원사업');

-- BC-FLOOD-INSURANCE-2026 / INSURANCE
INSERT INTO support_programs (external_code, name, description, target_description, operating_status, eligibility_conditions, supported_facilities, required_documents, application_method, application_status, application_start_date, application_end_date, official_source, official_url, program_kind, source_ids, source_verified_at, source_metadata, created_at)
SELECT 'BC-FLOOD-INSURANCE-2026',
       '풍수해·지진재해보험 보험료 지원',
       '자연재난 재산피해에 대비한 보험 가입 시 보험료 일부를 지원.',
       '경기도 부천시 (세부 지원 자격 확인 필요)',
       '문의·접수 여부 확인 필요',
       '주택(동산 포함), 농·임업용 온실, 소상공인 상가·공장 등 상품별 가입 대상 확인.
주택 세입자는 관할 지자체를 통한 가입 안내.',
       '[]',
       NULL,
       '국민안전24의 보험사 가입 안내 이용. 세입자·단체가입은 관할 지자체 확인.',
       'NEEDS_CONFIRMATION',
       NULL,
       NULL,
       '부천시·행정안전부 공식 안내',
       'https://news.bucheon.go.kr/home/kor/M366733089/sns/social/edit.do?idx=cb00d1a0008a85e71a41b8741facbffebf141d9d142c0e846f04b401a5841387',
       'INSURANCE',
       '["S4","S5"]',
       '2026-11-08',
       '{"year":2026,"kind":"INSURANCE","region":"경기도 부천시","action_tags":["INSURANCE"],"support_details":"부천시 2026년 안내상 보험료 55% 이상 지원·선택 가입. 가입 조건에 따라 지원율·보장 내용이 달라짐.","service_period_end":null,"documents_complete":false,"process":["가입 대상·상품·보험료 확인","가입 신청","계약·보장 개시","보장사고 발생 시 약관에 따른 보험금 청구"],"contact":{"department":"가입 보험사 또는 관할 지자체","phone":null},"limitations":["신청자별 가입 가능 여부·자부담·구비서류 확인 필요.","침수를 물리적으로 막는 시설 설치 지원이 아니라 피해 보상 제도.","일부 저소득층 전액 지원을 모든 주민에게 적용하지 말 것."],"sources":{"S4":{"title":"2026 부천시 보험 3종 안내","publisher":"부천시 생생부천","document_date":"2026-03-13","url":"https://news.bucheon.go.kr/home/kor/M366733089/sns/social/edit.do?idx=cb00d1a0008a85e71a41b8741facbffebf141d9d142c0e846f04b401a5841387"},"S5":{"title":"풍수해·지진재해보험","publisher":"행정안전부 국민안전24","document_date":null,"url":"https://www.safekorea.go.kr/safekorea-kor/dspo/wnds/wwcs/winWaterCitizenSafetyInsurance.do?menuSn=76"}}}',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM support_programs WHERE external_code = 'BC-FLOOD-INSURANCE-2026' OR name = '풍수해·지진재해보험 보험료 지원');
