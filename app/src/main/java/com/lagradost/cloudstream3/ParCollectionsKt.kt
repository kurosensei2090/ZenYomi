@file:JvmName("ParCollectionsKt")
package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Iterable<A>.apmap(f: suspend (A) -> B): List<B> = amap(f)

suspend fun <A, B> Iterable<A>.parMap(f: suspend (A) -> B): List<B> = amap(f)
