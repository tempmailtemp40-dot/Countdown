package com.daycountdown

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.ZonedDateTime

object CountdownScheduler {
    private const val CHANNEL_ID = "day_countdown"
    private const val NOTIF_ID = 1
    const val ACTION = "com.daycountdown.PHASE_CHANGE"

    // Change this number if your routine changes. The day ends at midnight (12 am).
    private const val WAKE_HOUR = 7   // 7 am

    // Notification styles
    const val STYLE_CLEAN = 0
    const val STYLE_BIG_PERCENT = 1
    const val STYLE_SOLID = 2
    const val STYLE_NIGHT = 3

    fun getStyle(context: Context): Int =
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("style", STYLE_CLEAN)

    fun setStyle(context: Context, style: Int) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putInt("style", style).apply()
    }

    private data class Phase(val isDay: Boolean, val start: Long, val end: Long)

    private fun currentPhase(now: ZonedDateTime = ZonedDateTime.now()): Phase {
        val today = now.toLocalDate()
        val wake = today.atTime(WAKE_HOUR, 0).atZone(now.zone).toInstant().toEpochMilli()
        val midnightStart = today.atStartOfDay(now.zone).toInstant().toEpochMilli()
        val nextMidnight = today.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        return if (now.hour >= WAKE_HOUR) Phase(true, wake, nextMidnight)
        else Phase(false, midnightStart, wake)
    }

    /** Posts the right countdown for right now and schedules the next refresh. */
    fun apply(context: Context) {
        val now = System.currentTimeMillis()
        val phase = currentPhase()
        val len = phase.end - phase.start
        val pct = ((now - phase.start) * 100 / len).toInt().coerceIn(0, 100)
        post(context, phase, pct, now)

        // Refresh the bar and percentage every 1%, and switch phase at 7 am and 12 am.
        val nextTick = phase.start + (pct + 1) * len / 100
        val next = if (nextTick >= phase.end) phase.end + 1000 else nextTick + 1000
        scheduleNext(context, next)
    }

    private fun post(context: Context, phase: Phase, pct: Int, now: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getTestService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Day countdown", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )

        val remaining = (phase.end - now).coerceAtLeast(1000)
        val style = getStyle(context)
        val compactLayout: Int
        val bigLayout: Int
        when (style) {
            STYLE_BIG_PERCENT -> { compactLayout = R.layout.notif_c_big_percent; bigLayout = R.layout.notif_big_percent }
            STYLE_SOLID -> { compactLayout = R.layout.notif_c_solid; bigLayout = R.layout.notif_solid }
            STYLE_NIGHT -> { compactLayout = R.layout.notif_c_night; bigLayout = R.layout.notif_night }
            else -> { compactLayout = R.layout.notif_c_clean; bigLayout = R.layout.notif_clean }
        }

        val base = SystemClock.elapsedRealtime() + remaining
        val title = if (phase.isDay) "Left in today\u2019s waking hours" else "Rest time. Your day starts in"

        fun RemoteViews.common() {
            setChronometerCountDown(R.id.chrono, true)
            setChronometer(R.id.chrono, base, null, true)
            setProgressBar(R.id.bar, 100, pct, false)
        }

        // Collapsed view (what the lock screen shows): everything, in small compact text.
        val compact = RemoteViews(context.packageName, compactLayout).apply {
            common()
            if (style == STYLE_BIG_PERCENT) {
                setTextViewText(R.id.cap_left, if (phase.isDay) "left" else "to 7 am")
                setTextViewText(R.id.cap_right, "done")
                setTextViewText(R.id.percent, "$pct%")
            } else {
                setTextViewText(R.id.title, title)
                setTextViewText(
                    R.id.label,
                    if (phase.isDay) "$pct% done \u00B7 resets at 12 am" else "$pct% done \u00B7 day starts at 7 am"
                )
            }
        }

        // Expanded view (after tapping the arrow): the full layout with labels.
        val big = RemoteViews(context.packageName, bigLayout).apply {
            common()
            when (style) {
                STYLE_NIGHT -> {
                    setTextViewText(R.id.title, title)
                    setTextViewText(R.id.percent, "$pct% done")
                }
                STYLE_BIG_PERCENT -> {
                    setTextViewText(R.id.cap_left, if (phase.isDay) "Time left" else "Day starts in")
                    setTextViewText(R.id.cap_right, "Done")
                    setTextViewText(R.id.percent, "$pct%")
                }
                else -> {
                    setTextViewText(R.id.title, title)
                    setTextViewText(
                        R.id.label,
                        if (phase.isDay) "$pct% of your day is done" else "$pct% of rest time is done"
                    )
                    setTextViewText(R.id.side, if (phase.isDay) "Resets at 12 am" else "Day starts at 7 am")
                }
            }
        }

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class/java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID
            .setSmallIcon(R.drawable.ic_hourglass)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(remaining)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    private fun scheduleNext(context: Context, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PhaseReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }
}
