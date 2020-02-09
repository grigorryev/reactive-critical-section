package grigorryev

import reactor.core.publisher.Mono
import java.util.*

/**
 * Dumb lambda wrapper. Receives notification when execution leaves critical section.
 */
class CriticalSectionListener {
    @Volatile
    var reaction: () -> Unit = { }

    fun doNotify() = reaction()
}

/**
 * Provides controller publishers that can be used to organize critical sections by Mono.zipWith(..)
 */
class ReactiveCriticalSection {
    private val listeners = LinkedList<CriticalSectionListener>()

    @Synchronized
    fun enter(): Mono<Unit> {

        // remember whether the queue was empty
        val isEmpty = listeners.isEmpty()

        // create new listener and push it to the queue
        val listener = CriticalSectionListener()
        listeners.addLast(listener)


        return if (isEmpty) {
            // if the queue was empty - return Mono that resolves immediately
            Mono.just(Unit)
        } else {
            // otherwise - return Mono that will be resolved asynchronously by listener
            val deferredMono = Mono
                .create<Unit> { sink ->
                    listener.reaction = { sink.success(Unit) }
                }
                .cache()

            // we make cache() + subscribe() here to ensure that Mono.create is executed
            // immediately and `listener.reaction` is initialized by the time of actual subscription
            deferredMono.subscribe()

            deferredMono
        }
    }

    @Synchronized
    fun leave() {
        listeners.pop()
        // if there are executions waiting in the queue, notify the next one
        if (listeners.isNotEmpty()) {
            listeners.first.doNotify()
        }
    }
}

fun <T> Mono<T>.enter(criticalSection: ReactiveCriticalSection): Mono<T> {
    return this.zipWith(criticalSection.enter())
        .map { it.t1 }
}

fun <T> Mono<T>.leave(criticalSection: ReactiveCriticalSection): Mono<T> {
    return this.doFinally { criticalSection.leave() }
}
