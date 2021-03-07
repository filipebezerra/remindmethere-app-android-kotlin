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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.databinding.DataBindingUtil
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import dev.filipebezerra.android.remindmethere.databinding.ActivityNewReminderBinding
import kotlin.math.roundToInt

class NewReminderActivity : BaseActivity(), OnMapReadyCallback {
    private lateinit var viewBinding: ActivityNewReminderBinding

    private lateinit var map: GoogleMap

    private var reminder = Reminder(latLng = null, radius = null, message = null)

    private val radiusBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            updateRadiusWithProgress(progress)

            showReminderUpdate()
        }
    }

    private fun updateRadiusWithProgress(progress: Int) {
        val radius = getRadius(progress)
        reminder.radius = radius
        viewBinding.radiusDescription.text =
            getString(R.string.radius_description, radius.roundToInt().toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityNewReminderBinding>(this, R.layout.activity_new_reminder)
            .apply {
                viewBinding = this
                val mapFragment = supportFragmentManager
                    .findFragmentById(R.id.map) as SupportMapFragment
                mapFragment.getMapAsync(this@NewReminderActivity)

                instructionTitle.visibility = View.GONE
                instructionSubtitle.visibility = View.GONE
                radiusBar.visibility = View.GONE
                radiusDescription.visibility = View.GONE
                message.visibility = View.GONE

                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMapToolbarEnabled = false

        centerCamera()

        showConfigureLocationStep()
    }

    private fun centerCamera() {
        intent.extras.takeIf {
            it != null &&
                    it.containsKey(EXTRA_LAT_LNG) &&
                    it.containsKey(EXTRA_ZOOM)
        }?.let {
            val latLng = it.get(EXTRA_LAT_LNG) as LatLng
            val zoom = it.get(EXTRA_ZOOM) as Float
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        }
    }

    private fun showConfigureLocationStep() {
        with(viewBinding) {
            marker.visibility = View.VISIBLE
            instructionTitle.visibility = View.VISIBLE
            instructionSubtitle.visibility = View.VISIBLE
            radiusBar.visibility = View.GONE
            radiusDescription.visibility = View.GONE
            message.visibility = View.GONE
            instructionTitle.text = getString(R.string.instruction_where_description)
            next.setOnClickListener {
                reminder.latLng = this@NewReminderActivity.map.cameraPosition.target
                showConfigureRadiusStep()
            }
        }

        showReminderUpdate()
    }

    private fun showConfigureRadiusStep() {
        with(viewBinding) {
            marker.visibility = View.GONE
            instructionTitle.visibility = View.VISIBLE
            instructionSubtitle.visibility = View.GONE
            radiusBar.visibility = View.VISIBLE
            radiusDescription.visibility = View.VISIBLE
            message.visibility = View.GONE
            instructionTitle.text = getString(R.string.instruction_radius_description)
            next.setOnClickListener {
                showConfigureMessageStep()
            }
            radiusBar.setOnSeekBarChangeListener(radiusBarChangeListener)
            updateRadiusWithProgress(radiusBar.progress)
        }

        map.animateCamera(CameraUpdateFactory.zoomTo(15f))

        showReminderUpdate()
    }

    private fun getRadius(progress: Int) = progress.inc().toDouble() * 100

    private fun showConfigureMessageStep() {
        with(viewBinding) {
            marker.visibility = View.GONE
            instructionTitle.visibility = View.VISIBLE
            instructionSubtitle.visibility = View.GONE
            radiusBar.visibility = View.GONE
            radiusDescription.visibility = View.GONE
            message.visibility = View.VISIBLE
            instructionTitle.text = getString(R.string.instruction_message_description)
            next.setOnClickListener {
                hideKeyboard(this@NewReminderActivity, message)

                reminder.message = message.text.toString()

                if (reminder.message.isNullOrEmpty()) {
                    message.error = getString(R.string.error_required)
                } else {
                    addReminder(reminder)
                }
            }
            message.requestFocusWithKeyboard()
        }

        showReminderUpdate()
    }

    private fun addReminder(reminder: Reminder) {
        getRepository().add(reminder,
            success = {
                setResult(Activity.RESULT_OK)
                finish()
            },
            failure = {
                Snackbar.make(viewBinding.main, it, Snackbar.LENGTH_LONG).show()
            })
    }

    private fun showReminderUpdate() {
        map.clear()
        showReminderInMap(this, map, reminder)
    }

    companion object {
        private const val EXTRA_LAT_LNG = "EXTRA_LAT_LNG"
        private const val EXTRA_ZOOM = "EXTRA_ZOOM"

        fun newIntent(context: Context, latLng: LatLng, zoom: Float): Intent {
            val intent = Intent(context, NewReminderActivity::class.java)
            intent
                .putExtra(EXTRA_LAT_LNG, latLng)
                .putExtra(EXTRA_ZOOM, zoom)
            return intent
        }
    }
}
