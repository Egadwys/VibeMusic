package com.ega.project.vibemusic
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Notifikasi "Stay awake for music" yang tampil selama fitur layar-tetap-menyala aktif. */
object StayAwake{
 private const val CH="stay_awake"
 private const val ID=7
 fun show(c:Context){
  val nm=c.getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(CH,"Stay awake",NotificationManager.IMPORTANCE_LOW))
  val pi=PendingIntent.getActivity(c,0,
   Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
   PendingIntent.FLAG_IMMUTABLE)
  nm.notify(ID,Notification.Builder(c,CH)
   .setSmallIcon(R.drawable.ic_vinyl)
   .setContentTitle("Stay awake for music")
   .setContentText("Layar tetap menyala saat musik diputar. Ketuk cover di pemutar untuk menonaktifkan.")
   .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build())
 }
 fun hide(c:Context)=c.getSystemService(NotificationManager::class.java).cancel(ID)
}
