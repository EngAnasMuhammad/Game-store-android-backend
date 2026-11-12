package com.anasdev

import com.anasdev.data.local.DatabaseFactory
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(environment.config)

    configureSerialization()
    configureSecurity()
    configureRouting()
    configureStatusPages()
}