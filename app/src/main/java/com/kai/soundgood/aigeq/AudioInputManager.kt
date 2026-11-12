package com.kai.soundgood.aigeq

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord

/** A selected mixer channel is NOT an audio capture device. */
data class CaptureDevice(val id:Int, val name:String, val usb:Boolean)
object AudioInputManager {
 fun available(context:Context):List<CaptureDevice> {
  val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  return manager.getDevices(AudioManager.GET_DEVICES_INPUTS).map {
   CaptureDevice(it.id, it.productName.toString(), it.type==AudioDeviceInfo.TYPE_USB_DEVICE || it.type==AudioDeviceInfo.TYPE_USB_HEADSET)
  }
 }
 /** Call before startRecording; verify routedDevice after startRecording. */
 fun route(record:AudioRecord,context:Context,deviceId:Int):Boolean {
  val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  val device=manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull{it.id==deviceId} ?: return false
  return record.setPreferredDevice(device)
 }
 fun routedId(record:AudioRecord):Int?=record.routedDevice?.id
}
