# Admin SaaS Server

Spring Boot + PostgreSQL backend for the Admin SaaS Layout Design frontend.

## Run PostgreSQL

```powershell
docker compose up -d
```

## Run server

```powershell
$env:OCR_COMMAND="C:\Program Files\Tesseract-OCR\tesseract.exe"
.\gradlew.bat bootRun
```

기본 HWP 명령은 프로젝트의 [convert-to-hwp.bat](/C:/Users/gustj/Downloads/Admin%20SaaS%20Layout%20Design/server/scripts/convert-to-hwp.bat) 를 사용합니다.
실제 변환기는 아래 둘 중 하나로 연결합니다.

```powershell
$env:HWP_NATIVE_CONVERTER_COMMAND="C:\path\to\native-converter.exe \"{docx}\" \"{output}\""
```

또는

```powershell
$env:HWP_NATIVE_CONVERTER_EXE="C:\path\to\native-converter.exe"
```

## Profiles

- Default profile: `dev`
- Production profile: `prod`

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
.\gradlew.bat bootRun
```

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:postgresql://db-host:5432/admin_saas"
$env:DB_USERNAME="admin"
$env:DB_PASSWORD="change-me"
$env:CORS_ALLOWED_ORIGINS="https://your-frontend.example.com"
$env:OCR_COMMAND="C:\Program Files\Tesseract-OCR\tesseract.exe"
$env:HWP_CONVERTER_COMMAND="scripts\\convert-to-hwp.bat \"{docx}\" \"{output}\" \"{hml}\""
$env:HWP_NATIVE_CONVERTER_COMMAND="C:\path\to\native-converter.exe \"{docx}\" \"{output}\""
java -jar build\libs\admin-saas-server-0.0.1-SNAPSHOT.jar
```

Render에서 운영 배포할 때는 네이티브 Java 서비스 대신 [server/Dockerfile](/C:/Users/gustj/Downloads/Admin%20SaaS%20Layout%20Design/server/Dockerfile) 기반 Docker 서비스를 사용해야 합니다. 이 이미지가 `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`를 설치하므로 `OCR_COMMAND=tesseract`만 유지하면 됩니다.

## Main API paths

- `GET /api/dashboard/overview?projectId=1`
- `GET /api/projects`
- `GET /api/projects/{id}`
- `POST /api/projects`
- `GET /api/projects/{id}/budget-rules`
- `PUT /api/projects/{id}/budget-rules`
- `GET /api/expenses?projectId=1`
- `POST /api/expenses`
- `GET /api/documents?projectId=1`
- `GET /api/documents/{id}`
- `POST /api/documents`
- `POST /api/documents/export/word`
- `GET /api/validations?projectId=1`
- `POST /api/validations/run?projectId=1`
- `POST /api/validations/{id}/resolve`
- `GET /api/settlements/latest?projectId=1`
- `POST /api/settlements/generate?projectId=1`
- `PUT /api/settlements/{id}`
- `POST /api/settlements/export/word`
- `PUT /api/projects/{id}`
- `GET /api/projects/{projectId}/files`
- `GET /api/projects/{projectId}/files/download?path=...`
- `DELETE /api/projects/{projectId}/files?path=...`
- `PUT /api/expenses/{id}`
- `DELETE /api/expenses/{id}`

## Health Check

- `GET /actuator/health`
- `GET /actuator/info`

## Recommended Environment Variables

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5432/admin_saas"
$env:DB_USERNAME="admin"
$env:DB_PASSWORD="admin1234"
$env:OCR_COMMAND="C:\Program Files\Tesseract-OCR\tesseract.exe"
$env:HWP_CONVERTER_COMMAND="scripts\\convert-to-hwp.bat \"{docx}\" \"{output}\" \"{hml}\""
$env:HWP_NATIVE_CONVERTER_COMMAND="C:\path\to\native-converter.exe \"{docx}\" \"{output}\""
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
```

## Operations

- Operational checklist: [OPERATIONS.md](/C:/Users/gustj/Downloads/Admin%20SaaS%20Layout%20Design/server/OPERATIONS.md)
- HWP export pipeline exists internally but is disabled in the user UI until a real converter is prepared
- Deployment guide: [DEPLOY_VERCEL_RENDER_NEON.md](/C:/Users/gustj/Downloads/Admin%20SaaS%20Layout%20Design/DEPLOY_VERCEL_RENDER_NEON.md)
