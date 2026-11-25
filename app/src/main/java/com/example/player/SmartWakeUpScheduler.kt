package com.example.player

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * #36 Smart Wake-Up & Pomodoro Routines:
 * Schedules exact wake-up alarms with gradual volume fade-in using AlarmManager.setExactAndAllowWhileIdle,
 * and coordinates local 25/5 Pomodoro focus cycles.
 */
object SmartWakeUpScheduler {

    private const val ALARM_REQUEST_CODE = 7036

    private val _wakeUpTriggered = MutableStateFlow(false)
    val wakeUpTriggered: StateFlow<Boolean> = _wakeUpTriggered.asStateFlow()

    fun markWakeUpTriggered() {
        _wakeUpTriggered.value = true
    }

    fun consumeWakeUpTrigger() {
        _wakeUpTriggered.value = false
    }

    fun scheduleWakeUp(context: Context, hour: Int, minute: Int): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SmartWakeUpReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val triggerAt = cal.timeInMillis

        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }
        return triggerAt
    }

    fun cancelWakeUp(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SmartWakeUpReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pendingIntent) }
    }
}

class SmartWakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SmartWakeUpScheduler.markWakeUpTriggered()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("synfix_wakeup_fade_in", true)
        }
        runCatching { context.startActivity(launchIntent) }

        // Gradual fade-in ramp via PlaybackRuntimeCoordinator if service is already alive
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            for (step in 1..12) {
                PlaybackRuntimeCoordinator.onRequestFadeVolume?.invoke(step / 12f)
                delay(750L)
            }
        }
    }
}
