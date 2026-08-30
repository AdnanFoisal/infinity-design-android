package com.adnanfoisal.infinitydesign

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Section 31: Hilt application. The DI graph wires up:
 *  - DB / DataStore / Repository
 *  - Dispatchers
 *  - Backend client (HTTP)
 *  - ProceduralRegistry / Typography engine
 */
@HiltAndroidApp
class InfinityDesignApplication : Application()
