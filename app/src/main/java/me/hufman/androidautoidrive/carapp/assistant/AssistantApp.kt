package me.hufman.androidautoidrive.carapp.assistant

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.utils.GraphicsHelpers
import me.hufman.androidautoidrive.carapp.AMAppList
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionStatus
import me.hufman.idriveconnectionkit.android.security.SecurityAccess

class AssistantApp(val iDriveConnectionStatus: IDriveConnectionStatus, val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val controller: AssistantController, val graphicsHelpers: GraphicsHelpers) {
	val TAG = "AssistantApp"
	val carConnection: BMWRemotingServer
	val amAppList: AMAppList<AssistantAppInfo>

	init {
		amAppList = AMAppList(graphicsHelpers, "me.hufman.androidautoidrive.assistant")
		val carappListener = CarAppListener(amAppList)
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)

		amAppList.connection = carConnection
		amAppList.callback = { assistant ->
			controller.triggerAssistant(assistant)
			Thread.sleep(2000)
			amAppList.redrawApp(assistant)
		}
	}

	fun onCreate() {
		val assistants = controller.getAssistants()
		amAppList.setApps(assistants.toList())
	}

	fun onDestroy() {
	}

	class CarAppListener(val amAppList: AMAppList<AssistantAppInfo>): BaseBMWRemotingClient() {
		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			appId ?: return
			val assistant = amAppList.getAppInfo(appId) ?: return
			amAppList.callback(assistant)
		}
	}
}