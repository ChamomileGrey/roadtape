/*
 * roadtape v0.1.0 — vehicle signal abstraction and the AAOS Car API implementation
 * Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Install path: app/src/main/java/com/chamomilegrey/roadtape/vehicle/VehicleSignalSource.kt
 * Changelog:
 *   v0.1.0 2026-11-13 Claude Fable 5.2 speed and gear from CarPropertyManager
 */
package com.chamomilegrey.roadtape.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Build
import kotlin.math.abs

/** One update from the vehicle. A null field means "not part of this update". */
data class VehicleSignal(val speedKph: Float? = null, val inPark: Boolean? = null)

/**
 * A source of live vehicle signals. Wiper and window state are not available
 * through the AAOS standard API used here; they are to be added as a second
 * implementation on top of the Pleos Connect Vehicle SDK (Wiper / Window APIs).
 */
interface VehicleSignalSource {
    /** Starts listening. Returns false if the vehicle API is unavailable. */
    fun start(onSignal: (VehicleSignal) -> Unit): Boolean

    fun stop()
}

/**
 * Reads speed and gear through the AAOS standard [CarPropertyManager].
 * Only construct this on a device that reports the automotive feature —
 * the android.car classes do not exist elsewhere.
 */
class CarPropertyVehicleSource(private val context: Context) : VehicleSignalSource {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var listener: ((VehicleSignal) -> Unit)? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val metersPerSecond = value.value as? Float ?: return
                    listener?.invoke(VehicleSignal(speedKph = abs(metersPerSecond) * 3.6f))
                }
                VehiclePropertyIds.GEAR_SELECTION -> {
                    val gear = value.value as? Int ?: return
                    listener?.invoke(VehicleSignal(inPark = gear == VehicleGear.GEAR_PARK))
                }
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    override fun start(onSignal: (VehicleSignal) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val connectedCar = Car.createCar(context) ?: return false
            val propertyManager =
                connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return false
            car = connectedCar
            manager = propertyManager
            listener = onSignal
            val speedOk = propertyManager.registerCallback(
                callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI
            )
            propertyManager.registerCallback(
                callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            speedOk
        } catch (e: Exception) {
            stop()
            false
        }
    }

    override fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
        listener = null
    }
}
