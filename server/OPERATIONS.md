# Operations Checklist

## Pre-Deploy

- Set `SPRING_PROFILES_ACTIVE=prod`
- Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Set `CORS_ALLOWED_ORIGINS`
- Set `OCR_COMMAND`
- Ensure PostgreSQL is reachable from the application host
- Ensure Tesseract is installed and `kor`, `eng` language packs exist
- Ensure upload storage path is writable via `UPLOAD_DIR`
- Review `ddl-auto=validate` compatibility against the target database schema
- Confirm health endpoints are reachable: `/actuator/health`, `/actuator/info`

## Release Validation

- Start the server with production profile
- Check `/actuator/health` returns `UP`
- Verify project list API responds
- Verify file upload path is writable
- Verify receipt OCR runs on one image file
- Verify settlement Word export downloads successfully

## Post-Deploy

- Check server logs for startup exceptions
- Check database connection pool stability
- Check disk usage on upload storage
- Check OCR execution latency on sample receipts
- Check CORS behavior from the deployed frontend origin

## Risks Still Open

- No authentication/authorization yet
- No audit log persistence yet
- Local filesystem storage is still used instead of object storage
- PDF export is not implemented yet
- HWP export is intentionally disabled in the user UI until a reliable converter is secured
- Integration tests for main business flows are not in place yet
