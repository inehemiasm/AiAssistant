package com.neo.chevere.shared

import platform.UIKit.UIDevice

internal actual fun platformName(): String = UIDevice.currentDevice.systemName
