package dev.filipebezerra.android.remindmethere.geofencing

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import dev.filipebezerra.android.remindmethere.Reminder
import dev.filipebezerra.android.remindmethere.ReminderApp
import dev.filipebezerra.android.remindmethere.sendNotification
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import timber.log.Timber

class GeofenceTransitionsJobIntentService : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        Timber.d("GeofenceTransitionsJobIntentService received work to handle")
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceErrorMessages.getErrorString(
                this,
                geofencingEvent.errorCode
            )
            Timber.e(errorMessage)
            return
        }
        handleEvent(geofencingEvent)
    }

    private fun handleEvent(event: GeofencingEvent) {
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Timber.d("GeofenceTransitionsJobIntentService is handling a GEOFENCE_TRANSITION_ENTER")
            val reminder = getFirstReminder(event.triggeringGeofences)
            val message = reminder?.message
            val latLng = reminder?.latLng
            if (message != null && latLng != null) {
                Timber.d("GeofenceTransitionsJobIntentService is dispatching the notification")
                sendNotification(this, message, latLng)
            } else {
                when {
                    message == null -> Timber.d("Notification not sent: message is null")
                    latLng == null -> Timber.d("Notification not sent: latLng is null")
                }
            }
        }
    }

    private fun getFirstReminder(triggeringGeofences: List<Geofence>): Reminder? {
        val firstGeofence = triggeringGeofences[0]
        return (application as ReminderApp).getRepository().get(firstGeofence.requestId)
    }

    companion object {
        private const val JOB_IB = 573

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java,
                JOB_IB,
                intent
            )
        }
    }
}