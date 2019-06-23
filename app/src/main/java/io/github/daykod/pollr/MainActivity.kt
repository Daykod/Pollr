package io.github.daykod.pollr

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.delay
import timber.log.Timber

class App : Application(), LifecycleObserver {

    lateinit var pollr: Pollr
        private set

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        pollr = Pollr(ProcessLifecycleOwner.get().lifecycle)
        app.pollr.register(TestFetcher(), TestRequest::class)
    }
}

val Context.app : App get() = applicationContext as App

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        app.pollr.request(TestRequest("500"))
    }

    override fun onDestroy() {
        super.onDestroy()
        app.pollr.cancel(TestRequest("500"))
    }
}


data class TestRequest(val param : String) : Request {
    override val interval: Int
        get() = 5
}

class TestFetcher : Fetcher<TestRequest> {
    override suspend fun fetch(request: TestRequest) {
        Timber.i("Fetching")
        delay(2)
        Timber.i("Storing")
        delay(2)
        Timber.i("Finished")
    }

}