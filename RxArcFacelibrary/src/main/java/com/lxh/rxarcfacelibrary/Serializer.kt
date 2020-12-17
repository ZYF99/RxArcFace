package com.lxh.rxarcfacelibrary

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * json Serializer
 */

val globalMoshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()