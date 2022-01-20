package com.example.musicplayerntt

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayList
import com.example.musicplayerntt.MainActivity

import android.content.Intent

import android.view.LayoutInflater

import android.view.ViewGroup

import android.widget.LinearLayout

import android.widget.TextView
import com.example.musicplayerntt.R


class SelectDeviceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_device)

        // Bluetooth Setup
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Get List of Paired Bluetooth Device
        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceList: MutableList<Any> = ArrayList()
        if (pairedDevices.size > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (device in pairedDevices) {
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address
            }
            // Display paired device using recyclerView
            val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewDevice)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.itemAnimator = DefaultItemAnimator()
        } else {
            val view = findViewById<View>(R.id.recyclerViewDevice)
            val snackbar = Snackbar.make(
                view,
                "Activate Bluetooth or pair a Bluetooth device",
                Snackbar.LENGTH_INDEFINITE
            )
            snackbar.setAction("OK") { }
            snackbar.show()
        }
    }
}