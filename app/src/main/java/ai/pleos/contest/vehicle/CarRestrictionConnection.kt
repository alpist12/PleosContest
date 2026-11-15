package ai.pleos.contest.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Loaded only on Automotive devices that supply the optional android.car library. */
class CarRestrictionConnection(private val context: Context) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var active = false

    fun start() {
        active = true
        DrivingGate.update(null)
        runCatching {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), 0L) { connected, ready ->
                if (!active) return@createCar
                if (!ready) { manager = null; DrivingGate.update(null); return@createCar }
                runCatching {
                    val m = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    manager = m
                    m.registerListener { restrictions ->
                        if (active) DrivingGate.update(restrictions.isRequiresDistractionOptimization)
                    }
                    DrivingGate.update(m.currentCarUxRestrictions.isRequiresDistractionOptimization)
                }.onFailure { DrivingGate.update(null) }
            }
        }.onFailure { DrivingGate.update(null) }
    }

    fun stop() {
        active = false
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null; car = null
        DrivingGate.update(null)
    }
}
