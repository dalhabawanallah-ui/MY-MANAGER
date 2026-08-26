package com.example.mymanager

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("tasks", MODE_PRIVATE)
    }

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf("android.permission.POST_NOTIFICATIONS"),
                10
            )
        }

        showHome()
    }

    private fun showHome() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(28, 28, 28, 28)

        val title = TextView(this)
        title.text = "MY MANAGER"
        title.textSize = 30f
        title.setTextColor(Color.rgb(20, 70, 130))
        title.gravity = Gravity.CENTER

        root.addView(title, LinearLayout.LayoutParams(-1, 70))

        val add = Button(this)
        add.text = "＋  ADD TASK"
        root.addView(add)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        root.addView(
            list,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)

        refresh()

        add.setOnClickListener {
            addTask()
        }
    }

    private fun refresh() {
        list.removeAllViews()

        val tasks = prefs.all.keys
            .filter { it.startsWith("t_") }
            .sorted()

        if (tasks.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No tasks yet.\nAdd your first task."
            empty.textSize = 18f
            empty.setPadding(0, 40, 0, 0)
            list.addView(empty)
            return
        }

        for (key in tasks) {
            val value = prefs.getString(key, "") ?: ""

            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, 14, 0, 14)

            val text = TextView(this)
            text.text = value
            text.textSize = 18f

            row.addView(text)

            val done = Button(this)
            done.text = "Mark Complete"

            row.addView(done)

            done.setOnClickListener {
                prefs.edit()
                    .remove(key)
                    .apply()

                refresh()
            }

            list.addView(row)
        }
    }

    private fun addTask() {

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(30, 10, 30, 0)

        val name = EditText(this)
        name.hint = "Task name"
        box.addView(name)

        val time = TimePicker(this)
        time.setIs24HourView(false)
        box.addView(time)

        val reminder = Spinner(this)

        reminder.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                "5 minutes",
                "10 minutes",
                "15 minutes",
                "30 minutes",
                "1 hour"
            )
        )

        box.addView(reminder)

        AlertDialog.Builder(this)
            .setTitle("New Task")
            .setView(box)
            .setPositiveButton("SAVE") { _, _ ->

                val calendar = Calendar.getInstance()

                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    time.hour
                )

                calendar.set(
                    Calendar.MINUTE,
                    time.minute
                )

                calendar.set(
                    Calendar.SECOND,
                    0
                )

                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }

                val reminderMinutes = intArrayOf(
                    5,
                    10,
                    15,
                    30,
                    60
                )[reminder.selectedItemPosition]

                val trigger =
                    calendar.timeInMillis -
                    reminderMinutes * 60000L

                val key =
                    "t_" + String.format(
                        "%013d",
                        calendar.timeInMillis
                    )

                val formattedTime =
                    SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(calendar.time)

                val taskText =
                    "${name.text} • $formattedTime • reminder $reminderMinutes min before"

                prefs.edit()
                    .putString(key, taskText)
                    .apply()

                scheduleReminder(
                    key,
                    taskText,
                    trigger
                )

                refresh()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun scheduleReminder(
        key: String,
        text: String,
        triggerTime: Long
    ) {

        if (triggerTime <= System.currentTimeMillis()) {
            return
        }

        val alarmManager =
            getSystemService(ALARM_SERVICE) as AlarmManager

        if (
            android.os.Build.VERSION.SDK_INT >= 31 &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
        }

        val intent =
            Intent(
                this,
                ReminderReceiver::class.java
            ).putExtra(
                "text",
                "Reminder: $text"
            )

        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    "reminders",
                    "MY MANAGER Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                )

            notificationManager.createNotificationChannel(channel)
        }

        val notification =
            Notification.Builder(
                context,
                "reminders"
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle("MY MANAGER")
                .setContentText(
                    intent.getStringExtra("text")
                )
                .setAutoCancel(true)
                .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
