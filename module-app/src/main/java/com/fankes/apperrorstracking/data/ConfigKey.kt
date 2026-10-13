package com.fankes.apperrorstracking.data

/** Typed key with the original on-disk name and default. */
data class ConfigKey<T>(val key: String, val default: T)