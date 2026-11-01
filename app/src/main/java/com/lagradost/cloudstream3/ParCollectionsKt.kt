@file:JvmName("ParCollectionsKt")
package com.lagradost.cloudstream3

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Iterable parallel operations
 */
suspend fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    ensureActive()
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Iterable<A>.apmap(f: suspend (A) -> B): List<B> = amap(f)

suspend fun <A, B> Iterable<A>.parMap(f: suspend (A) -> B): List<B> = amap(f)

/**
 * List parallel operations
 */
suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    ensureActive()
    map { async { f(it) } }.awaitAll()
}

fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> List<A>.amapIndexed(f: suspend (index: Int, A) -> B): List<B> = coroutineScope {
    ensureActive()
    mapIndexed { index, a -> async { f(index, a) } }.awaitAll()
}

fun <A, B> List<A>.apmapIndexed(f: suspend (index: Int, A) -> B): List<B> = runBlocking {
    mapIndexed { index, a -> async { f(index, a) } }.awaitAll()
}

/**
 * Map parallel operations
 */
suspend fun <K, V, R> Map<out K, V>.amap(f: suspend (Map.Entry<K, V>) -> R): List<R> = coroutineScope {
    ensureActive()
    map { async { f(it) } }.awaitAll()
}

fun <K, V, R> Map<out K, V>.apmap(f: suspend (Map.Entry<K, V>) -> R): List<R> = runBlocking {
    map { async { f(it) } }.awaitAll()
}

/**
 * Variadic functions
 */
fun <R> argamap(vararg transforms: suspend () -> R): List<R?> = runBlocking {
    transforms.map {
        async {
            try {
                it.invoke()
            } catch (_: Exception) {
                null
            }
        }
    }.awaitAll()
}

suspend fun <R> runAllAsync(vararg transforms: suspend () -> R): List<R?> = coroutineScope {
    ensureActive()
    transforms.map { fn ->
        async {
            try {
                fn.invoke()
            } catch (_: Exception) {
                null
            }
        }
    }.awaitAll()
}

/**
 * Collection cmap
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <A, B> Collection<A>.cmap(f: suspend (A) -> B): List<B> =
    with(CoroutineScope(currentCoroutineContext())) {
        map { async { f(it) } }.map { deferred ->
            try {
                Result.success(deferred.await())
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    deferred.join()
                }
                Result.failure(e)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }.map { it.getOrThrow() }
    }
