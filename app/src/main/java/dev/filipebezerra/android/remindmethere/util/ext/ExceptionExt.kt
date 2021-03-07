package dev.filipebezerra.android.remindmethere.util.ext

import android.content.Context
import dev.filipebezerra.android.remindmethere.geofencing.GeofenceErrorMessages

fun Exception.getHumanReadableErrorMessage(context: Context): String =
    GeofenceErrorMessages.getErrorString(context, this)