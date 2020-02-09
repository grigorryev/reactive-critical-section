# reactive-critical-section

Usage:

```kotlin
val criticalSection = ReactiveCriticalSection()

userSsoSessionsRedisRepository.removeExpiredSessions(updatedSession.user.userId)
    .enter(criticalSection)    // <-- enter
    .flatMap { sessionKey2SessionIdRepository.save(session) }
    .flatMap { userSsoSessionsRedisRepository.save(session) }
    .flatMap { ssoSessionRepository.save(session) }
    .leave(criticalSection)    // <-- leave
    .doOnNext { publishEvent(session, SessionEventType.UPDATE) }
    .map { updatedSession }
```