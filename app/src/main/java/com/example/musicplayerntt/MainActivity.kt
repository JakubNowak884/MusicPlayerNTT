package com.example.musicplayerntt

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import java.io.File
import java.io.IOException
import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.ProgressBar
import android.widget.TextView
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.*
import android.content.ContentValues.TAG
import android.util.Log
import android.widget.Toast
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), MediaPlayer.OnPreparedListener {

    private var length = 0
    private var playerReady = false

    private var songTitle: TextView? = null
    private var songLength: TextView? = null
    private var songProgress: ProgressBar? = null
    private var deviceName: TextView? = null
    private var deviceStatus: TextView? = null

    private var mMediaPlayer: MediaPlayer? = null
    private var songLengthThread: SongLengthThread? = null

    var handler: Handler? = null
    var mmSocket: BluetoothSocket? = null
    var connectedThread: ConnectedThread? = null
    private var createConnectThread: CreateConnectThread? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val connectingStatus = 1 // used in bluetooth handler to identify message status
    private val messageRead = 2 // used in bluetooth handler to identify message update

    private var songRequestCode = 0
    private var bluetoothDeviceRequestCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        songTitle = findViewById(R.id.songTitle)
        songProgress = findViewById(R.id.songProgressBar)
        songLength = findViewById(R.id.songLength)
        deviceName = findViewById(R.id.deviceName)
        deviceStatus = findViewById(R.id.deviceStatus)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val bLoad: Button = findViewById(R.id.b_load)
        bLoad.setOnClickListener {
            ActivityCompat.requestPermissions((this as Activity?)!!, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                val musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val allFiles = musicDirectory.listFiles()
                if (allFiles != null && allFiles.isNotEmpty()) {
                    val listOfMP3Files = allFiles.toMutableList()
                    listOfMP3Files.removeAll(predicate = { file: File -> !file.name.endsWith(".mp3") })
                    val listOfNames : ArrayList<String> = ArrayList()
                    val listOfAddresses : ArrayList<String> = ArrayList()
                    for (file in allFiles) {
                        listOfNames.add(file.name)
                        listOfAddresses.add(file.absolutePath)
                    }
                    val intent = Intent(this, ListActivity::class.java)
                    intent.putStringArrayListExtra("listOfNames", listOfNames)
                    intent.putStringArrayListExtra("listOfAddresses", listOfAddresses)
                    startActivityForResult(intent, songRequestCode)
                }
                else {
                    Toast.makeText(this, "The are no .mp3 files in your music directory.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val bSelect: Button = findViewById(R.id.b_select)
        bSelect.setOnClickListener {
            ActivityCompat.requestPermissions((this as Activity?)!!, arrayOf(Manifest.permission.BLUETOOTH), 0)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                val pairedDevices = bluetoothAdapter?.bondedDevices
                if (pairedDevices != null && pairedDevices.isNotEmpty()) {
                    val listOfNames : ArrayList<String> = ArrayList()
                    val listOfAddresses : ArrayList<String> = ArrayList()
                    for (device in pairedDevices) {
                        listOfNames.add(device.name)
                        listOfAddresses.add(device.address)
                    }
                    val intent = Intent(this, ListActivity::class.java)
                    intent.putStringArrayListExtra("listOfNames", listOfNames)
                    intent.putStringArrayListExtra("listOfAddresses", listOfAddresses)
                    startActivityForResult(intent, bluetoothDeviceRequestCode)
                }
                else {
                    Toast.makeText(this, "The are no paired devices.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val bPlay: Button = findViewById(R.id.b_play)
        bPlay.setOnClickListener {
            if (playerReady)
            {
                mMediaPlayer?.seekTo(length)
                mMediaPlayer?.start()
                playerReady = false
            }
            else
            {
                length = mMediaPlayer?.currentPosition!!
                mMediaPlayer?.stop()
                mMediaPlayer?.prepareAsync()
            }
        }

        /*
        Second most important piece of Code. GUI Handler
         */

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    connectingStatus -> when (msg.arg1) {
                        1 -> deviceStatus?.text = getString(R.string.connected)
                        -1 -> deviceStatus?.text = getString(R.string.disconnected)
                    }
                    messageRead -> {
                        val arduinoMsg: String = msg.obj.toString() // Read message from Arduino
                        when (arduinoMsg.lowercase(Locale.getDefault())) {
                            "start" -> {
                                mMediaPlayer?.seekTo(length)
                                mMediaPlayer?.start()
                                playerReady = false
                            }
                            "stop" -> {
                                length = mMediaPlayer?.currentPosition!!
                                mMediaPlayer?.stop()
                                mMediaPlayer?.prepareAsync()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intentData: Intent?) {
        super.onActivityResult(requestCode, resultCode, intentData)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                songRequestCode ->  {
                    intentData?.let { data ->
                        val path = data.getStringExtra("address")
                        val name = data.getStringExtra("name")

                        mMediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                        }
                        mMediaPlayer?.setDataSource(path)
                        mMediaPlayer?.apply {
                            setOnPreparedListener(this@MainActivity)
                            prepareAsync()
                        }

                        songTitle?.text = name
                        val songLengthText = "0:00 - " + mMediaPlayer?.duration.toString()
                        songLength?.text = songLengthText

                        songLengthThread = SongLengthThread(mMediaPlayer!!)
                        songLengthThread!!.start()
                    }
                }

                bluetoothDeviceRequestCode ->  {
                    intentData?.let { data ->
                        val address = data.getStringExtra("address")
                        val name = data.getStringExtra("name")

                        createConnectThread = CreateConnectThread(bluetoothAdapter!!, address)
                        createConnectThread!!.start()

                        deviceStatus?.text = getString(R.string.connecting)

                        val deviceNameText = "Name: $name"
                        deviceName?.text = deviceNameText
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        createConnectThread?.cancel()
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        playerReady = true
    }

    inner class SongLengthThread(_mediaPlayer: MediaPlayer) : Thread() {

        override fun run() {
            while (true) {
                songProgress?.max = mMediaPlayer?.duration!!
                songProgress?.progress = mMediaPlayer?.currentPosition!!
                val songLengthText = mMediaPlayer?.currentPosition.toString() + " - " + mMediaPlayer?.duration.toString()
                songLength?.text = songLengthText
            }
        }

    }

    inner class CreateConnectThread(bluetoothAdapter: BluetoothAdapter, address: String?) :
        Thread() {
        override fun run() {
            ActivityCompat.requestPermissions((this@MainActivity as Activity?)!!, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 0)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                try {
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    mmSocket?.connect()
                    handler?.obtainMessage(connectingStatus, 1, -1)?.sendToTarget()
                } catch (connectException: IOException) {
                    // Unable to connect; close the socket and return.
                    try {
                        mmSocket?.close()
                        Log.e("Status", "Cannot connect to device")
                        handler?.obtainMessage(connectingStatus, -1, -1)?.sendToTarget()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close the client socket", closeException)
                    }
                    return
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                connectedThread = mmSocket?.let { ConnectedThread(it) }
                connectedThread!!.run()
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }

        init {
            ActivityCompat.requestPermissions((this@MainActivity as Activity?)!!, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 0)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                /*
                Use a temporary object that is later assigned to mmSocket
                because mmSocket is final.
                 */
                val bluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                var tmp: BluetoothSocket? = null
                val uuid: UUID = bluetoothDevice.uuids[0].uuid
                try {
                    /*
                    Get a BluetoothSocket to connect with the given BluetoothDevice.
                    Due to Android device varieties,the method below may not work fo different devices.
                    You should try using other methods i.e. :
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                     */
                    tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid)
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's create() method failed", e)
                }
                if (tmp != null) {
                    mmSocket = tmp
                }
            }
        }
    }

    inner class ConnectedThread(mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            val buffer = ByteArray(1024) // buffer store for the stream
            var bytes = 0 // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                        Read from the InputStream from Arduino until termination character is reached.
                        Then send the whole String message to GUI Handler.
                         */
                    buffer[bytes] = mmInStream?.read()!!.toByte()
                    var readMessage: String
                    if (buffer[bytes] == '\n'.code.toByte()) {
                        readMessage = String(buffer, 0, bytes)
                        Log.e("Arduino Message", readMessage)
                        handler?.obtainMessage(messageRead, readMessage)?.sendToTarget()
                        bytes = 0
                    } else {
                        bytes++
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }
}

