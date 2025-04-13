package org.jugendhackt.wegweiser.di

import org.jugendhackt.wegweiser.MainViewModel
import org.jugendhackt.wegweiser.dvb.DVBSource
import org.jugendhackt.wegweiser.sensors.shake.ShakeSensor
import org.jugendhackt.wegweiser.tts.TTS
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::DVBSource)
    singleOf(::TTS)
    singleOf(::ShakeSensor)

    viewModelOf(::MainViewModel)
}