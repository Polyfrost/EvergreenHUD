package org.polyfrost.evergreenhud.client.utils

import org.jetbrains.skia.Canvas

// Skiko 0.999.6 made Matrix33 a value class, changing the getter's JVM name
// and return type. Resolve once so the same mod jar works with both runtimes.
private val matrixGetter = Canvas::class.java.methods.single {
    it.parameterCount == 0 &&
        (it.name == "getLocalToDeviceAsMatrix33" || it.name.startsWith("getLocalToDeviceAsMatrix33-"))
}
private val matrixValuesGetter = if (matrixGetter.returnType == FloatArray::class.java) null
    else matrixGetter.returnType.getMethod("getMat")

internal fun Canvas.transformValues(): FloatArray {
    val matrix = matrixGetter.invoke(this)
    return (matrixValuesGetter?.invoke(matrix) ?: matrix) as FloatArray
}
