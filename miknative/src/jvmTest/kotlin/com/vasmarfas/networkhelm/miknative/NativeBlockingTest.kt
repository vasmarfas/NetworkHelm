package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class QuietStream : ByteStream {
    private val closed = CountDownLatch(1)

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = withContext(Dispatchers.IO) {
        closed.await()
        throw IOException("Socket closed")
    }

    override suspend fun write(bytes: ByteArray) = Unit

    override fun close() = closed.countDown()
}

class NativeBlockingTest {
    @Test
    fun aRouterThatNeverAnswersTheLoginIsGivenUpAtTheDeadline() = runBlocking {
        val error = assertFailsWith<DeviceException> {
            withTimeout(10_000) { NativeConnection.open(QuietStream(), "admin", "secret", this, loginTimeoutMillis = 200) }
        }
        assertEquals(ErrorCategory.ConnectionLost, error.category)
    }

    @Test
    fun aCallerThatGivesUpIsNotHeldByAQuietRouter() = runBlocking {
        val attempt = launch { NativeConnection.open(QuietStream(), "admin", "secret", this) }
        delay(200)
        withTimeout(5_000) { attempt.cancelAndJoin() }
    }
}
