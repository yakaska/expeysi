package com.expeysi.app.data.remote

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Буфер отказов сервера без потерь: каждое [offer] гарантированно доходит до
 * потребителя — событие остаётся в канале, пока его не заберут, и при
 * всплесках не отбрасывается (SharedFlow с нулевым replay терял бы и то, и
 * другое: без подписки событие исчезало, при переполнении буфера tryEmit
 * возвращал false). offer не приостанавливается и не блокируется: колбэк
 * отказа транспорта живёт на обычном потоке (у Firebase — на главном).
 * Поток рассчитан на одного потребителя (receiveAsFlow): при нескольких
 * сборщиках события разделились бы между ними.
 */
class RejectionBuffer {
    private val channel = Channel<SyncKey>(Channel.UNLIMITED)

    /**
     * Записывает отказ в буфер. На UNLIMITED-канале, который здесь никогда
     * не закрывается, trySend всегда успешен: отбрасывать нечего.
     */
    fun offer(key: SyncKey) {
        channel.trySend(key)
    }

    /** Единственный потребитель забирает события в порядке [offer]. */
    val flow: Flow<SyncKey> = channel.receiveAsFlow()
}
