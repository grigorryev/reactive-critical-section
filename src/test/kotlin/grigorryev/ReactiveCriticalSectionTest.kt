package grigorryev

import org.junit.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReactiveCriticalSectionTest {
    @Volatile var unsafeCounter = 0

    fun decrease(): Mono<Unit> {
        return Mono.just(Unit)
            .doOnNext { unsafeCounter -= 1 }
            .subscribeOn(Schedulers.parallel())
    }

    fun increase(): Mono<Unit> {
        return Mono.just(Unit)
            .doOnNext { unsafeCounter += 1 }
            .subscribeOn(Schedulers.parallel())
    }

    @Volatile var safeCounter = 0

    fun decreaseSafely(cs: ReactiveCriticalSection): Mono<Unit> {
        return Mono.just(Unit)
            .enter(cs)
            .doOnNext { safeCounter -= 1 }
            .leave(cs)
            .subscribeOn(Schedulers.parallel())
    }

    fun increaseSafely(cs: ReactiveCriticalSection): Mono<Unit> {
        return Mono.just(Unit)
            .enter(cs)
            .doOnNext { safeCounter += 1 }
            .leave(cs)
            .subscribeOn(Schedulers.parallel())
    }

    @Test
    fun failsWithoutCriticalSection() {
        val increasers = Flux.range(0, 1_000_000)
            .flatMap { increase() }

        val decreasers = Flux.range(0, 1_000_000)
            .flatMap { decrease() }

        Flux.merge(increasers, decreasers).collectList().block()

        assertNotEquals(unsafeCounter, 0)
    }

    @Test
    fun worksWithCriticalSection() {
        val cs = ReactiveCriticalSection()

        val increasers = Flux.range(0, 1_000_000)
            .flatMap { increaseSafely(cs) }

        val decreasers = Flux.range(0, 1_000_000)
            .flatMap { decreaseSafely(cs) }

        Flux.merge(increasers, decreasers).collectList().block()

        assertEquals(safeCounter, 0)
    }
}