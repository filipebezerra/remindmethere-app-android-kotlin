package dev.filipebezerra.android.remindmethere.geofencing

import android.content.Context
import dev.filipebezerra.android.remindmethere.R
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.GeofenceStatusCodes

/**
 * Use [GeofenceStatusCodes] to translate to human-readable error messages.
 *
 * **See also: [GeofenceStatusCodes source](https://developers.google.com/android/reference/com/google/android/gms/location/GeofenceStatusCodes)**
 */
object GeofenceErrorMessages {
    fun getErrorString(context: Context, e: Exception): String {
        return if (e is ApiException) {
            getErrorString(context, e.statusCode)
        } else {
            context.resources.getString(R.string.geofence_unknown_error)
        }
    }

    fun getErrorString(context: Context, errorCode: Int): String {
        val resources = context.resources
        return when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                resources.getString(R.string.geofence_not_available)

            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                resources.getString(R.string.geofence_too_many_geofences)

            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS ->
                resources.getString(R.string.geofence_too_many_pending_intents)

            GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION ->
                resources.getString(R.string.geofence_missing_permission_background_location)

            else -> resources.getString(R.string.geofence_unknown_error)
        }
    }
}