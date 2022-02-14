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
import android.app.AlertDialog
import android.content.Context

import androidx.core.app.ActivityCompat

import android.content.pm.PackageManager

import androidx.core.content.ContextCompat
import android.content.DialogInterface
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.bluetooth.BluetoothSocket

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.*
import android.content.ContentValues.TAG
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), MediaPlayer.OnPreparedListener {

    private var newFileActivityRequestCode = 1

    private var mMediaPlayer: MediaPlayer? = null
    private var pathToCurrentSong: String? = null
    private var playerReady = false
    private var allFiles: List<File> = emptyList<File>()
    private lateinit var songTitle: TextView
    private lateinit var songLength: TextView
    private lateinit var songProgress: ProgressBar
    private var length = 0

    private var deviceName: String? = null
    private var deviceAddress: String? = null
    var handler: Handler? = null
    var mmSocket: BluetoothSocket? = null
    var connectedThread: ConnectedThread? = null
    var createConnectThread: CreateConnectThread? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val CONNECTING_STATUS = 1 // used in bluetooth handler to identify message status

    private val MESSAGE_READ = 2 // used in bluetooth handler to identify message update

    val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        songTitle = findViewById(R.id.songTitle)
        songProgress = findViewById(R.id.songProgressBar)
        songLength = findViewById(R.id.t_length)

        val bLoad: Button = findViewById(R.id.b_load)
        bLoad.setOnClickListener {
            ActivityCompat.requestPermissions((this as Activity?)!!, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                val musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                var allFiles = musicDirectory.listFiles().toMutableList()
                allFiles.removeAll(predicate = { file: File -> !file.name.endsWith(".mp3") })
                val listOfNames : ArrayList<String> = ArrayList()
                val listOfAddresses : ArrayList<String> = ArrayList()
                for (file in allFiles) {
                    listOfNames.add(file.name)
                    listOfAddresses.add(file.absolutePath)
                }
                val intent = Intent(this, ListActivity::class.java)
                intent.putStringArrayListExtra("listOfNames", listOfNames)
                intent.putStringArrayListExtra("listOfAddresses", listOfAddresses)
                startActivityForResult(intent, newFileActivityRequestCode)
            }
        }

        val bPlay: Button = findViewById(R.id.b_play)
        bPlay.setOnClickListener {
            if (playerReady)
            {
                mMediaPlayer?.seekTo(length);
                mMediaPlayer?.start()
                playerReady = false
            }
            else
            {
                length= mMediaPlayer?.currentPosition!!;
                mMediaPlayer?.stop()
                mMediaPlayer?.prepareAsync()
            }
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        /*
        Second most important piece of Code. GUI Handler
         */

        handler = object : Handler(Looper.getMainLooper()) {
            val bConnect: Button = findViewById(R.id.b_connect)
            val tStatus: TextView = findViewById(R.id.t_status)
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    CONNECTING_STATUS -> when (msg.arg1) {
                        1 -> {
                            tStatus.text = "Status: connected to " + deviceName
                            bConnect.isEnabled = true
                        }
                        -1 -> {
                            tStatus.text = "Status: disconnected"
                            bConnect.isEnabled = true
                        }
                    }
                    MESSAGE_READ -> {
                        val arduinoMsg: String = msg.obj.toString() // Read message from Arduino
                        println("Message received")
                        when (arduinoMsg.toLowerCase()) {
                            "start" -> {
                                mMediaPlayer?.seekTo(length);
                                mMediaPlayer?.start()
                                playerReady = false
                            }
                            "stop" -> {
                                length= mMediaPlayer?.currentPosition!!;
                                mMediaPlayer?.stop()
                                mMediaPlayer?.prepareAsync()
                            }
                        }
                    }
                }
            }
        }

        // Select Bluetooth Device
        val bConnect: Button = findViewById(R.id.b_connect)
        // Select Bluetooth Device
        bConnect.setOnClickListener { // Move to adapter list

            // Get List of Paired Bluetooth Device
            if (checkPermissionREAD_EXTERNAL_STORAGE(this)) {
                val pairedDevices = bluetoothAdapter?.bondedDevices
                deviceName = pairedDevices?.first()?.name
                deviceAddress = pairedDevices?.first()?.address
                val tStatus: TextView = findViewById(R.id.t_status)
                tStatus.text = "Status: disconnected " + deviceName
                if (deviceName != null) {
                    /*
                    This is the most important piece of code. When "deviceName" is found
                    the code will call a new thread to create a bluetooth connection to the
                    selected device (see the thread code below)
                     */

                    createConnectThread = CreateConnectThread(bluetoothAdapter!!, deviceAddress)
                    createConnectThread!!.start()
                    val tStatus: TextView = findViewById(R.id.t_status)
                    tStatus.text = "Status: connecting to... " + deviceName
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intentData: Intent?) {
        super.onActivityResult(requestCode, resultCode, intentData)

        /* Inserts flower into viewModel. */
        if (requestCode == newFileActivityRequestCode && resultCode == Activity.RESULT_OK) {
            intentData?.let { data ->
                val absolutePath = data.getStringExtra("absolutePath")
                val name = data.getStringExtra("name")

                mMediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                }

                val musicDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                allFiles = musicDirectory.listFiles().toList()
                mMediaPlayer?.setDataSource(absolutePath)

                mMediaPlayer?.apply {
                    setOnPreparedListener(this@MainActivity)
                    prepareAsync() // prepare async to not block main thread
                }

                songTitle.text = name
                songLength.text = "0:00 - " + mMediaPlayer?.duration.toString()
            }
        }
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        playerReady = true;
        songProgress.max = mediaPlayer.duration
        songProgress.progress = mediaPlayer.currentPosition
        songLength.text = mediaPlayer.currentPosition.toString() + " - " + mMediaPlayer?.duration.toString()
    }

    inner class CreateConnectThread(bluetoothAdapter: BluetoothAdapter, address: String?) :
        Thread() {
        override fun run() {
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket?.connect()
                Log.e("Status", "Device connected")
                handler?.obtainMessage(CONNECTING_STATUS, 1, -1)?.sendToTarget()
            } catch (connectException: IOException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket?.close()
                    Log.e("Status", "Cannot connect to device")
                    handler?.obtainMessage(CONNECTING_STATUS, -1, -1)?.sendToTarget()
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

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }

        init {
            /*
                Use a temporary object that is later assigned to mmSocket
                because mmSocket is final.
                 */
            val bluetoothDevice = bluetoothAdapter?.bondedDevices.first()
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

    inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
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
                    buffer[bytes] = mmInStream?.read()!!?.toByte()
                    var readMessage: String
                    if (buffer[bytes] == '\n'.code.toByte()) {
                        readMessage = String(buffer, 0, bytes)
                        Log.e("Arduino Message", readMessage)
                        handler?.obtainMessage(MESSAGE_READ, readMessage)?.sendToTarget()
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

        /* Call this from the main activity to send data to the remote device */
        fun write(input: String) {
            val bytes = input.toByteArray() //converts entered String into bytes
            try {
                mmOutStream?.write(bytes)
            } catch (e: IOException) {
                Log.e("Send Error", "Unable to send message", e)
            }
        }

        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
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

    private fun checkPermissionREAD_EXTERNAL_STORAGE(
        context: Context?
    ): Boolean {
        val currentAPIVersion = Build.VERSION.SDK_INT
        return if (currentAPIVersion >= Build.VERSION_CODES.M) {
            if (context?.let {
                    ContextCompat.checkSelfPermission(
                        it,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                } != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        (context as Activity?)!!,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                ) {
                    showDialog(
                        "External storage", context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    ActivityCompat
                        .requestPermissions(
                            (context as Activity?)!!,
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                            MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                        )
                }
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    private fun showDialog(
        msg: String, context: Context?,
        permission: String
    ) {
        val alertBuilder: AlertDialog.Builder = AlertDialog.Builder(context)
        alertBuilder.setCancelable(true)
        alertBuilder.setTitle("Permission necessary")
        alertBuilder.setMessage("$msg permission is necessary")
        alertBuilder.setPositiveButton(android.R.string.yes,
            DialogInterface.OnClickListener { dialog, which ->
                ActivityCompat.requestPermissions(
                    (context as Activity?)!!, arrayOf(permission),
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                )
            })
        val alert: AlertDialog = alertBuilder.create()
        alert.show()
    }
}

