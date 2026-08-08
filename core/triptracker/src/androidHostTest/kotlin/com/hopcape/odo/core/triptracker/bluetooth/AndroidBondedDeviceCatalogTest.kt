package com.hopcape.odo.core.triptracker.bluetooth

import android.bluetooth.BluetoothClass
import com.hopcape.odo.core.triptracker.DeviceCategory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [classify]'s device-class -> [DeviceCategory] table — pure, so it runs with no real
 * `BluetoothAdapter`/`BluetoothDevice` (the [BluetoothClass] constants are plain ints,
 * resolvable against the Android stub jar with no device or Robolectric involved).
 */
class AndroidBondedDeviceCatalogTest {

    @Test
    fun carAudio_classifiesAsCarAudio() {
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
            deviceClass = BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
        )
        assertEquals(DeviceCategory.CAR_AUDIO, category)
    }

    @Test
    fun wearableHeadset_classifiesAsHeadset() {
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
            deviceClass = BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        )
        assertEquals(DeviceCategory.HEADSET, category)
    }

    @Test
    fun handsfree_classifiesAsHeadset() {
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
            deviceClass = BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
        )
        assertEquals(DeviceCategory.HEADSET, category)
    }

    @Test
    fun wearableMajorClass_classifiesAsWearable() {
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.WEARABLE,
            deviceClass = BluetoothClass.Device.Major.WEARABLE,
        )
        assertEquals(DeviceCategory.WEARABLE, category)
    }

    @Test
    fun phone_classifiesAsOther() {
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.PHONE,
            deviceClass = BluetoothClass.Device.PHONE_SMART,
        )
        assertEquals(DeviceCategory.OTHER, category)
    }

    @Test
    fun unrecognizedAudioVideoMinor_classifiesAsOther() {
        // A speaker or camcorder — AUDIO_VIDEO major, but not one of the two headset/car
        // minors this catalog cares about.
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
            deviceClass = BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
        )
        assertEquals(DeviceCategory.OTHER, category)
    }

    @Test
    fun computerMajorClass_classifiesAsOther() {
        val category = classify(
            majorDeviceClass = BluetoothClass.Device.Major.COMPUTER,
            deviceClass = BluetoothClass.Device.COMPUTER_LAPTOP,
        )
        assertEquals(DeviceCategory.OTHER, category)
    }
}
