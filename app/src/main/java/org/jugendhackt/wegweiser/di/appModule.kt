package org.jugendhackt.wegweiser.di

import org.jugendhackt.wegweiser.MainViewModel
import org.jugendhackt.wegweiser.dvb.DVBSource
import org.jugendhackt.wegweiser.dvb.StationStore
import org.jugendhackt.wegweiser.sensors.shake.ShakeSensor
import org.jugendhackt.wegweiser.tts.TTS
import org.jugendhackt.wegweiser.language.language
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::StationStore)
    singleOf(::DVBSource)
    singleOf(::TTS)
    singleOf(::language)
    singleOf(::ShakeSensor)


    viewModelOf(::MainViewModel)
}
