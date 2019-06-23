package io.github.daykod.pollr

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

interface Request {
    val interval : Int
}

interface Fetcher<T : Request> {
    suspend fun fetch(request : T)
}

class Pollr @Inject constructor(lifecycle: Lifecycle) : CoroutineScope,  LifecycleObserver {
    override val coroutineContext: CoroutineContext = Dispatchers.IO

    private var pollJob : Job? = null

    private val fetchers = mutableMapOf<KClass<*>, Fetcher<Request>>()
    private val requests = mutableSetOf<Request>()
    private val timers = mutableMapOf<Request, Int>()
    private val stopwatch = Stopwatch()

    init {
        lifecycle.addObserver(this)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Request> register(fetcher: Fetcher<T>, clazz: KClass<*>) {
        fetchers[clazz] = fetcher as Fetcher<Request>
    }

    fun request(vararg request: Request) {
        request.forEach {
            if(fetchers[it::class] == null) {
                throw IllegalStateException("Fetcher for request of type ${it.javaClass.simpleName} must be registered")
            }
            Timber.i("Accepting $it")
            requests.add(it)
        }

        if(pollJob == null) {
            pollJob = start()
        }
    }

    fun cancel(vararg request: Request) {
        request.forEach {
            Timber.i("Removing $it")
            requests.remove(it)
        }
    }

    private fun start() = launch {
        Timber.i("Starting polling")
        while(requests.isNotEmpty()) {
            val secondsSince = stopwatch.secondsSince()
            requests.forEach {
                timers[it] = timers[it]?.let { it + secondsSince }?: Int.MAX_VALUE
            }
            Timber.i("$secondsSince second(s) has passed")
            requests.forEach {request ->
                if(request.interval <= timers[request] ?: 0) {
                    Timber.i("Fetching $request")
                    timers[request] = 0
                    fetchers[request::class]?.fetch(request)
                }
            }
            delay(TimeUnit.SECONDS.toMillis(1))
        }
    }.apply {
        invokeOnCompletion {
            pollJob = null
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        Timber.i("onStop")
        pollJob?.cancel()
        pollJob = null
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        Timber.i("onStart")
        if(pollJob == null) {
            pollJob = start()
        }
    }
}

private class Stopwatch {
    var lastTime : Long = 0

    fun secondsSince() : Int {
        val curTime = System.currentTimeMillis()
        val diff = ((curTime + 100) - lastTime)
        lastTime = curTime
        return  TimeUnit.MILLISECONDS.toSeconds(diff).toInt()
    }
}

