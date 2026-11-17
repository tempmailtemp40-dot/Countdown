package com.daycountdown

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val info = TextView(this).apply {
            textSize = 18f
            text = "The countdown is running as a notification.\n\n" +
                "From 7 am it counts down your 17 waking hours to 12 am. " +
                "From 12 am to 7 am it counts down to the next morning. It resets by itself every day.\n\n" +
                "To see it on the lock screen, open Settings > Notifications > Notifications on lock screen " +
                "and choose to show all content.\n\n" +
                "Pick a style:"
        }

        val names = listOf("Clean bar", "Big percent", "Solid colour card", "Night blue")
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        names.forEachIndexed { i, name ->
            group.addView(RadioButton(this).apply {
                id = 100 + i
                text = name
                textSize = 18f
                isChecked = (i == CountdownScheduler.getStyle(this@MainActivity))
            })
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            CountdownScheduler.setStyle(this, checkedId - 100)
            CountdownScheduler.apply(this)
        }

        val refresh = Button(this).apply {
            text = "Restart countdown"
            setOnClickListener { CountdownScheduler.apply(this@MainActivity) }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(info)
            addView(group)
            addView(refresh)
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            CountdownScheduler.apply(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CountdownScheduler.apply(this)
    }
}
