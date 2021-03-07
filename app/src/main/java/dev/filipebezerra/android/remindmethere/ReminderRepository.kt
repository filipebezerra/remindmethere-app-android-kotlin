/*
 * Copyright (c) 2018 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package dev.filipebezerra.android.remindmethere

import android.Manifest.permission
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.content.ContextCompat.checkSelfPermission
import dev.filipebezerra.android.remindmethere.geofencing.GeofenceBroadcastReceiver
import dev.filipebezerra.android.remindmethere.geofencing.GeofenceErrorMessages
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson

class ReminderRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "ReminderRepository"
        private const val REMINDERS = "REMINDERS"
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun add(
        reminder: Reminder,
        success: () -> Unit,
        failure: (error: String) -> Unit
    ) {
        val geofence = buildGeofence(reminder)
        if (geofence != null &&
            checkSelfPermission(context, permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
        ) {
            geofencingClient.addGeofences(buildGeofencingRequest(geofence), geofencePendingIntent)
                .addOnSuccessListener {
                    saveAll(getAll() + reminder)
                    success()
                }
                .addOnFailureListener { failure(GeofenceErrorMessages.getErrorString(context, it)) }
        }
    }

    private fun buildGeofence(reminder: Reminder): Geofence? {
        val latitude = reminder.latLng?.latitude
        val longitude = reminder.latLng?.longitude
        val radius = reminder.radius
        if (latitude != null && longitude != null && radius != null) {
            return Geofence.Builder()
                .setRequestId(reminder.id)
                .setCircularRegion(
                    latitude,
                    longitude,
                    radius.toFloat()
                )
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        return null
    }

    private fun buildGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .build()
    }

    fun remove(
        reminder: Reminder,
        success: () -> Unit,
        failure: (error: String) -> Unit
    ) {
        val list = getAll() - reminder
        saveAll(list)
        success()
    }

    private fun saveAll(list: List<Reminder>) {
        preferences
            .edit()
            .putString(REMINDERS, gson.toJson(list))
            .apply()
    }

    fun getAll(): List<Reminder> {
        if (preferences.contains(REMINDERS)) {
            val remindersString = preferences.getString(REMINDERS, null)
            val arrayOfReminders = gson.fromJson(
                remindersString,
                Array<Reminder>::class.java
            )
            if (arrayOfReminders != null) {
                return arrayOfReminders.toList()
            }
        }
        return listOf()
    }

    fun get(requestId: String?) = getAll().firstOrNull { it.id == requestId }

    fun getLast() = getAll().lastOrNull()
}