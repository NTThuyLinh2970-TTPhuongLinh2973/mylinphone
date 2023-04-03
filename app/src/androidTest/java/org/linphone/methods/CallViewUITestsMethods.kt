package org.linphone.methods

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.widget.Chronometer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.util.*
import kotlinx.coroutines.*
import org.linphone.R
import org.linphone.core.AuthInfo
import org.linphone.core.Call
import org.linphone.methods.UITestsUtils.activityScenario
import org.linphone.methods.UITestsUtils.checkWithTimeout
import org.linphone.utils.AppUtils.Companion.getString

object CallViewUITestsMethods {
    const val TIMEOUT = 10.0

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val manager = UITestsCoreManager.instance
    var appAccountAuthInfo: AuthInfo = UITestsCoreManager.instance.appAccountAuthInfo
    var ghostAccount: UITestsRegisteredLinphoneCore = UITestsCoreManager.instance.ghostAccounts[0]
    var startCallTime = Date().time // for checkCallTime function

    fun refreshAccountInfo() {
        appAccountAuthInfo = UITestsCoreManager.instance.appAccountAuthInfo
        ghostAccount = UITestsCoreManager.instance.ghostAccounts[0]
    }

    fun startIncomingCall() {
        if (ghostAccount.callState != Call.State.Released) { ghostAccount.terminateCall() }

        ghostAccount.startCall(manager.createAddress(appAccountAuthInfo))
        startCallTime = Date().time

        ghostAccount.waitForCallState(Call.State.OutgoingRinging, TIMEOUT)
        waitForCallNotification(true, TIMEOUT)
    }

    fun startOutgoingCall() {
        if (ghostAccount.callState != Call.State.Released) { ghostAccount.terminateCall() }

        onView(withId(R.id.sip_uri_input)).perform(typeText(ghostAccount.mAuthInfo.username))
        onView(withContentDescription(R.string.content_description_start_call)).perform(click())
        startCallTime = Date().time

        UITestsView.outgoingCallView.checkWithTimeout(matches(isDisplayed()), TIMEOUT)
        ghostAccount.waitForCallState(Call.State.IncomingReceived, TIMEOUT)
    }

    fun endCall(currentView: ViewInteraction? = null) {
        if (ghostAccount.callState == Call.State.Released) { return }

        ghostAccount.terminateCall()
        ghostAccount.waitForCallState(Call.State.Released, 5.0)
        currentView?.checkWithTimeout(doesNotExist(), 5.0)
        waitForCallNotification(false, 5.0)
    }

    fun checkCallTime(view: ViewInteraction, launchTime: Long = Date().time) = runBlocking {
        view.checkWithTimeout(matches(isDisplayed()), TIMEOUT)
        val firstValue = ((Date().time - launchTime) / 1000).toInt() + 1
        val wait = launch(Dispatchers.Default) {
            val timerArray = arrayListOf<Int>()
            repeat(3) {
                view.check { view, _ ->
                    val value = (view as Chronometer).text.toString()
                    timerArray.add((value.split(":").last()).toInt())
                }
                delay(1000)
            }
            assert(timerArray.distinct().size >= 2) { "[UITests] Call Time is not correctly incremented, less than 2 different values are displayed in 3 seconds" }
            assert(timerArray == timerArray.sorted()) { "[UITests] Call Time is not correctly incremented, it is not increasing" }
            assert(timerArray.first() <= firstValue + 3) { "[UITests] Call Time is not correctly initialized, it is at ${timerArray.first()}, $firstValue seconds after the start)" }
        }
        wait.join()
    }

    fun onPushAction(label: String, resultingView: ViewInteraction?, timeout: Double = TIMEOUT) {
        try {
            val button = device.findObject(By.textContains(label))
            button.click()
        } catch (e: java.lang.NullPointerException) {
            throw AssertionError("[UITests] Enable to find the \"$label\" button in the incoming call notification")
        }
        resultingView?.checkWithTimeout(matches(isDisplayed()), timeout)
    }

    fun onCallAction(
        id: Int,
        resultingView: ViewInteraction?,
        assertion: ViewAssertion,
        timeout: Double = 5.0
    ) {
        onView(withId(id)).checkWithTimeout(matches(isDisplayed()), timeout)
        onView(withId(id)).perform(click())
        resultingView?.checkWithTimeout(assertion, TIMEOUT)
    }

    fun waitForCallNotification(exist: Boolean, timeout: Double) = runBlocking {
        var result = !exist
        val wait = launch(Dispatchers.Default) {
            lateinit var activity: Activity
            activityScenario!!.onActivity { act -> activity = act }
            val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            repeat((timeout * 10).toInt()) {
                for (notif in manager.activeNotifications) {
                    if (notif.notification.channelId == getString(R.string.notification_channel_incoming_call_id)) {
                        result = true
                        break
                    }
                    result = false
                }
                if (manager.activeNotifications.isEmpty()) result = false
                if (result == exist) cancel()
                delay(100)
            }
        }
        wait.join()
        delay(1000)
        assert(result == exist) { "[UITests] Incoming call Notification still ${if (exist) "not " else ""}displayed after $timeout seconds" }
    }
}
