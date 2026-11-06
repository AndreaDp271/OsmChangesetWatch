package org.osmwatch.changesetwatch.domain

import kotlinx.coroutines.CancellationException

/**
 * Come [runCatching], ma lascia passare la cancellazione delle coroutine: runCatching la
 * inghiottirebbe e un lavoro annullato continuerebbe come se fosse fallito.
 */
inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Testo breve per l'utente: il messaggio dell'eccezione o, se manca, il suo tipo. */
val Throwable.reason: String
    get() = message ?: javaClass.simpleName
