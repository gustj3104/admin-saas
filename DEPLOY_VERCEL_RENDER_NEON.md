# Vercel + Render + Neon Deployment

이 프로젝트는 다음 구성으로 배포합니다.

- 프론트: Vercel
- 백엔드: Render
- DB: Neon Postgres

## 1. Neon DB 생성

1. Neon에서 Postgres 프로젝트를 생성합니다.
2. connection string을 복사합니다.
3. 접속 정보에서 다음 값을 확인합니다.

- host
- port
- database
- username
- password

Spring에서는 아래 형식으로 씁니다.

```text
jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require
```

예시:

```text
jdbc:postgresql://ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

## 2. Render 백엔드 배포

Render에서 새 Web Service를 만들고 이 저장소를 연결합니다.

- Root Directory: `server`
- Build Command: `./gradlew bootJar`
- Start Command: `java -jar build/libs/admin-saas-server-0.0.1-SNAPSHOT.jar`

환경변수:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require
DB_USERNAME=YOUR_NEON_USERNAME
DB_PASSWORD=YOUR_NEON_PASSWORD
CORS_ALLOWED_ORIGINS=https://your-vercel-domain.vercel.app
OCR_COMMAND=tesseract
```

헬스체크:

- `https://your-render-service.onrender.com/actuator/health`

주의:

- Render free는 유휴 시 sleep 될 수 있습니다.
- 현재 업로드 파일은 서버 로컬 디스크에 저장됩니다.
- Render free의 파일 시스템은 영구 저장소가 아니므로, 영수증/템플릿 업로드 파일은 재배포나 재시작 시 유실될 수 있습니다.

즉 지금 구조는 `데모/테스트 배포` 용도로는 가능하지만, 업로드 파일 보존이 중요한 실사용 배포에는 S3 같은 외부 스토리지가 추가로 필요합니다.

## 3. Vercel 프론트 배포

Vercel에서 이 저장소를 import 합니다.

- Framework Preset: `Vite`
- Build Command: `npm run build`
- Output Directory: `dist`

환경변수:

```text
VITE_API_BASE_URL=https://your-render-service.onrender.com/api
```

이 저장소에는 SPA 라우팅을 위해 `vercel.json`이 포함되어 있습니다.

## 4. CORS 설정

Render 환경변수 `CORS_ALLOWED_ORIGINS`에 실제 Vercel 주소를 넣어야 합니다.

예시:

```text
https://admin-saas-layout-design.vercel.app
```

커스텀 도메인을 붙이면 그 주소로 바꿔야 합니다.

## 5. 배포 후 확인 순서

1. Render health check 확인
2. Vercel 접속 확인
3. 프로젝트 생성
4. DB 저장 확인
5. 영수증 업로드 확인
6. 증빙 문서 Word 다운로드 확인

## 6. 현재 배포에서 바로 되는 것

- Neon PostgreSQL 연결
- 프로젝트/예산/지출/증빙 문서 API
- Vercel에서 Render API 호출
- 프로젝트별 docx 템플릿으로 Word 다운로드

## 7. 현재 무료 배포에서 제한되는 것

- 업로드 파일 영구 저장
- 안정적인 OCR 실행 보장
- sleep 없는 상시 응답

필요하면 다음 단계로

- S3/R2 업로드 저장소 연동
- Render/Neon 환경변수 값 실제 입력 포맷 정리
- Vercel/Render 배포 체크리스트 축약본 작성

