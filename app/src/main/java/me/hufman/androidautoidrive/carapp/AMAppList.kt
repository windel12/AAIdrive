package me.hufman.androidautoidrive.carapp

import android.graphics.drawable.Drawable
import android.util.Log
import de.bmw.idrive.BMWRemotingServer
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import kotlin.collections.HashMap
import kotlin.math.min
import kotlin.math.roundToInt

enum class AMCategory(val value: String) {
	ADDRESSBOOK("Addressbook"),
	MULTIMEDIA("Multimedia"),
	NAVIGATION("Navigation"),
	ONLINE_SERVICES("OnlineServices"),
	PHONE("Phone"),
	RADIO("Radio"),
	SETTINGS("Settings"),
	VEHICLE_INFORMATION("VehicleInformation"),
}

interface AMAppInfo {
	val packageName: String
	val name: String
	val icon: Drawable
	val category: AMCategory

	val amAppIdentifier
		get() = "androidautoidrive.$packageName"

	val weight
		get() = 800 - getAppWeight(this.name)

	companion object {
		fun getAppWeight(appName: String): Int {
			val name = appName.toLowerCase().toCharArray().filter { it.isLetter() }
			var score = min(name[0].toInt() - 'a'.toInt(), 'z'.toInt())
			score = score * 6 + ((name[1].toInt() / 6.0).roundToInt())
			return score
		}
	}
}

class ConcreteAMAppInfo(override val packageName: String, override val name: String,
                        override val icon: Drawable, override val category: AMCategory): AMAppInfo

class AMAppList<T: AMAppInfo>(val graphicsHelpers: GraphicsHelpers, val amIdent: String) {
	companion object {
		val TAG = "AMAppList"
	}

	var connection: BMWRemotingServer? = null
		set(value) {
			field = value
			amHandle = createAm()
			setApps(allApps)
		}

	private var amHandle: Int = 0
	private var allApps = emptyList<T>()
	private val knownApps = HashMap<String, T>()        // keyed by amAppIdentifier

	var callback: (T) -> Unit = { }

	fun getAMInfo(app: AMAppInfo): Map<Int, Any> {
		val amInfo = mutableMapOf<Int, Any>(
				0 to 145,   // basecore version
				1 to app.name,  // app name
				2 to graphicsHelpers.compress(app.icon, 48, 48), // icon
				3 to app.category.value,   // section
				4 to true,
				5 to app.weight,   // weight
				8 to -1  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = app.name
		}

		return amInfo
	}

	/**
	 * When handling an am_onAppEvent, this function
	 * converts the given appId to the original appInfo object
	 */
	fun getAppInfo(appId: String): T? {
		return knownApps[appId]
	}

	/**
	 * Updates the list of displayed apps
	 */
	fun setApps(apps: List<T>) {
		allApps = apps      // the desired apps, remembered across connections
		val connection = this.connection ?: return
		synchronized(connection) {
			// if there are any extra apps, clear out the list
			val updatedApps = apps.map {it.amAppIdentifier to it}.toMap()
			val stillCurrent = knownApps.values.filter { previous ->
				val updated = updatedApps[previous.amAppIdentifier]
				updated != null &&
					previous.name == updated.name &&
					previous.category == updated.category
			}
			if (stillCurrent.size < knownApps.size) {
				reinitAm()
			}

			// then create all the apps
			for (app in apps) {
				if (!knownApps.containsKey(app.amAppIdentifier)) {
					createApp(app)
					knownApps[app.amAppIdentifier] = app
				}
			}
		}
	}

	fun redrawApp(app: T) {
		if (knownApps.containsKey(app.amAppIdentifier)) {
			createApp(app)
		}
	}

	private fun reinitAm() {
		val connection = connection ?: return
		synchronized(connection) {
			val oldHandle = amHandle
			connection.am_removeAppEventHandler(oldHandle, amIdent)
			connection.am_dispose(oldHandle)

			val newHandle = createAm()
			amHandle = newHandle
		}
	}

	private fun createAm(): Int {
		val connection = connection ?: return 0
		return synchronized(connection) {
			val handle = connection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
			connection.am_addAppEventHandler(handle, amIdent)
			knownApps.clear()   // prepare to draw all new list
			handle
		}
	}

	private fun createApp(app: T) {
		Log.d(TAG, "Creating am app for app ${app.name}")
		val connection = connection ?: return
		synchronized(connection) {
			connection.am_registerApp(amHandle, app.amAppIdentifier, getAMInfo(app))
		}
	}
}