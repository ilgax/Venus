package dev.xcyn.venus

import net.fabricmc.api.ClientModInitializer

class VenusMod : ClientModInitializer {
    override fun onInitializeClient() {
        println("Venus mod loaded!")
    }
}