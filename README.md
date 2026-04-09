# Rate Limit Demo

Redis와 Lua 스크립트를 이용한 IP 기반 API 요청 제한 데모입니다.  
Spring Boot + Redis + React, Docker Compose로 실행합니다.

## 기술 스택

- Backend: Spring Boot 3.2, Java 17
- Rate Limiting: Redis 7, Lua Script (원자적 INCR + EXPIRE)
- Frontend: React 19, TypeScript, Vite
- 인프라: Docker, Nginx

## 동작 원리

요청마다 `RateLimitInterceptor`가 Redis에서 IP별 카운터를 확인합니다.  
`INCR`과 `EXPIRE`를 Lua 스크립트로 묶어 원자적으로 처리해 경쟁 조건 없이 카운팅합니다.

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
elseif count > tonumber(ARGV[2]) then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return count
```

## 제한 정책

- 일반 API (normal): 1초 / 20회
- API 호출 데모 (api): 10초 / 10회
- 한도 초과 시 `429 Too Many Requests` 반환
- 응답 헤더: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- Redis 미연결 시 Spring 제한은 skip되지만 Nginx 1차 제한(50r/s)은 유지됨 (graceful degradation)

## 실행

```bash
docker compose up -d --build
```

브라우저에서 `http://localhost:3000` 접속.

```bash
docker compose down
```
