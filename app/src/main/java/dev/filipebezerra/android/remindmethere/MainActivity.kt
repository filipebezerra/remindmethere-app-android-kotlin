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

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.material.snackbar.Snackbar
import dev.filipebezerra.android.remindmethere.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : BaseActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    private var map: GoogleMap? = null

    private lateinit var locationManager: LocationManager
    private lateinit var viewBinding: ActivityMainBinding

    private val runningQOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
            .apply {
                viewBinding = this
                val mapFragment = supportFragmentManager
                    .findFragmentById(R.id.map) as SupportMapFragment
                mapFragment.getMapAsync(this@MainActivity)

                newReminder.visibility = View.GONE
                currentLocation.visibility = View.GONE
                newReminder.setOnClickListener {
                    this@MainActivity.map?.run {
                        val intent = NewReminderActivity.newIntent(
                            this@MainActivity,
                            cameraPosition.target,
                            cameraPosition.zoom
                        )
                        startActivityForResult(intent, NEW_REMINDER_REQUEST_CODE)
                    }
                }

                locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            }
    }

    override fun onStart() {
        super.onStart()
        checkPermissionsAndStartGeofencing()
    }

    /**
     * Starts the permission check and Geofence process only if the Geofence associated with the
     * current hint isn't yet active.
     */
    private fun checkPermissionsAndStartGeofencing() {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartMap()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }

    /*
     *  Determines whether the app has the appropriate permissions across Android 10+ and all other
     *  Android versions.
     */
    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                )
        val backgroundLocationApproved = if (runningQOrLater) {
            PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else
            true
        Timber.d("Checking foreground and background permissions granted: %b",
                "${foregroundLocationApproved && backgroundLocationApproved}")
        return foregroundLocationApproved && backgroundLocationApproved
    }

    /*
     *  Uses the Location Client to check the current state of location settings, and gives the user
     *  the opportunity to turn on location services within our app.
     */
    private fun checkDeviceLocationSettingsAndStartMap(resolve: Boolean = true) {
        // TODO: Improve using chaining caills reducing the number of variables declaration
        Timber.d("Checking device location settings and start Geofence")
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val locationSettingsRequestBuilder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val settingsClient = LocationServices.getSettingsClient(this)
        val locationSettingsResponseTask = settingsClient
            .checkLocationSettings(locationSettingsRequestBuilder.build())
        locationSettingsResponseTask.addOnFailureListener { exception ->
            // TODO: Fix addOnFailureListener callback being called twice, even after user pressed
            //  OK on the Enable Device Settings dialog
            Timber.e("Requesting to check device location settings failed: $exception")
            if (exception is ResolvableApiException && resolve) {
                Timber.d("Error is resolvable so we'll start resolution")
                try {
                    exception.startResolutionForResult(
                        this,
                        REQUEST_TURN_DEVICE_LOCATION_ON
                    )
                } catch (sendExc: IntentSender.SendIntentException) {
                    // TODO: Improve better error handling, in this case some handling specific
                    //  for Play Services errors
                    Timber.e(
                        sendExc,
                        "Error getting location settings resolution: %s",
                        sendExc.message
                    )
                }
            } else {
                Timber.d("Prompting user to try check device settings and start Geofence again")
                Snackbar.make(
                    viewBinding.main,
                    getString(R.string.location_required_error),
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.try_again) {
                    checkDeviceLocationSettingsAndStartMap()
                }.show();
            }
        }
        locationSettingsResponseTask.addOnSuccessListener {
            Timber.d("Requesting to check device location settings was successful")
            onMapAndPermissionReady()
        }
    }

    /*
     *  Requests ACCESS_FINE_LOCATION and (on Android 10+ (Q) ACCESS_BACKGROUND_LOCATION.
     */
    @TargetApi(29 )
    private fun requestForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved())
            return
        var permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val requestCode = when {
            runningQOrLater -> {
                Timber.d("Requesting foreground and background location permissions")
                permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> {
                Timber.d("Requesting only foreground location permission")
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            }
        }
        ActivityCompat.requestPermissions(
            this,
            permissionsArray,
            requestCode
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NEW_REMINDER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            showReminders()

            val reminder = getRepository().getLast()
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(reminder?.latLng, 15f))

            Snackbar.make(viewBinding.main, R.string.reminder_added_success, Snackbar.LENGTH_LONG).show()
        }
    }

    /*
     * In all cases, we need to have the location permission.  On Android 10+ (Q) we need to have
     * the background permission as well.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Timber.d(
            "onRequestPermissionsResult() resulted %s for permissions %s",
            grantResults.joinToString(
                prefix = "[", postfix = "]"
            ) {
                if (it == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
            },
            permissions.joinToString(prefix = "[", postfix = "]")
        )
        if (
            grantResults.isEmpty() ||
            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
            (
                    requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE &&
                            grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED
                    )
        ) {
            Snackbar.make(
                viewBinding.main,
                getString(R.string.permission_denied_explanation),
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.settings) {
                startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.show();
        } else
            checkDeviceLocationSettingsAndStartMap()
    }

    private fun onMapAndPermissionReady() {
        if (map != null
            && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            map?.isMyLocationEnabled = true
            viewBinding.newReminder.visibility = View.VISIBLE
            viewBinding.currentLocation.visibility = View.VISIBLE

            viewBinding.currentLocation.setOnClickListener {
                locationManager.getBestProvider(Criteria(), false)?.let { bestProvider ->
                    val location = locationManager.getLastKnownLocation(bestProvider)
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }
            }

            showReminders()
            centerCamera()
        }
    }

    private fun centerCamera() {
        intent.extras.takeIf { it != null && it.containsKey(EXTRA_LAT_LNG) }?.let {
            val latLng = it.get(EXTRA_LAT_LNG) as LatLng
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun showReminders() {
        map?.run {
            clear()
            for (reminder in getRepository().getAll()) {
                showReminderInMap(this@MainActivity, this, reminder)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.run {
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false
            setOnMarkerClickListener(this@MainActivity)
        }

        onMapAndPermissionReady()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val reminder = getRepository().get(marker.tag as String)

        if (reminder != null) {
            showReminderRemoveAlert(reminder)
        }

        return true
    }

    private fun showReminderRemoveAlert(reminder: Reminder) {
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.run {
            setMessage(getString(R.string.reminder_removal_alert))
            setButton(
                AlertDialog.BUTTON_POSITIVE,
                getString(R.string.reminder_removal_alert_positive)
            ) { dialog, _ ->
                removeReminder(reminder)
                dialog.dismiss()
            }
            setButton(
                AlertDialog.BUTTON_NEGATIVE,
                getString(R.string.reminder_removal_alert_negative)
            ) { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    private fun removeReminder(reminder: Reminder) {
        getRepository().remove(
            reminder,
            success = {
                showReminders()
                Snackbar.make(viewBinding.main, R.string.reminder_removed_success, Snackbar.LENGTH_LONG).show()
            },
            failure = {
                Snackbar.make(viewBinding.main, it, Snackbar.LENGTH_LONG).show()
            })
    }

    companion object {
        private const val NEW_REMINDER_REQUEST_CODE = 330
        private const val EXTRA_LAT_LNG = "EXTRA_LAT_LNG"
        private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
        private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
        private const val LOCATION_PERMISSION_INDEX = 0
        private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1

        fun newIntent(context: Context, latLng: LatLng): Intent {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(EXTRA_LAT_LNG, latLng)
            return intent
        }
    }
}
