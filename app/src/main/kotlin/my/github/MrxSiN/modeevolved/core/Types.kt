package my.github.MrxSiN.modeevolved.core

/** [value] as a [T], or null when it is some other type. */
fun <T> Class<T>.castOrNull(value: Any?): T? = if (isInstance(value)) cast(value) else null
