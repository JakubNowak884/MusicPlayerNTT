package com.example.musicplayerntt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import android.text.InputType
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.youtube.player.YouTubeInitializationResult
import com.google.android.youtube.player.YouTubePlayer
import com.google.android.youtube.player.YouTubePlayerSupportFragment
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity(), YouTubePlayer.OnInitializedListener {

    private var musicPlaying = false
    private var m_Text = ""

    private var songTitle: TextView? = null
    private var songLength: TextView? = null
    private var songProgress: ProgressBar? = null
    private var deviceName: TextView? = null
    private var deviceStatus: TextView? = null
    private var bPlay: ImageButton? = null

    private var mediaPlayer: MediaPlayer? = null
    private var songLengthThread: SongLengthThread? = null

    private var youtubeFragment: YouTubePlayerSupportFragment? = null

    private var handler: Handler? = null
    private var mmSocket: BluetoothSocket? = null
    private var connectedThread: ConnectedThread? = null
    private var createConnectThread: CreateConnectThread? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val connectingStatus = 1 // used in bluetooth handler to identify message status
    private val messageRead = 2 // used in bluetooth handler to identify message update

    private var songRequestCode = 0
    private var bluetoothDeviceRequestCode = 1

    private var player : YouTubePlayer? = null

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

        val bLoadFromFiles: Button = findViewById(R.id.b_load_files)
        bLoadFromFiles.setOnClickListener {
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

        val bLoadFromYoutube: Button = findViewById(R.id.b_load_youtube)
        bLoadFromYoutube.setOnClickListener {
            ActivityCompat.requestPermissions((this as Activity?)!!, arrayOf(Manifest.permission.INTERNET), 0)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
                @Suppress("CAST_NEVER_SUCCEEDS") //because YouTube SDK doesn't use Androidx.
                youtubeFragment = supportFragmentManager.findFragmentById(R.id.youtube_fragment) as YouTubePlayerSupportFragment
                youtubeFragment?.initialize("AIzaSyBXneFQ6WeXoUW_YFvvw_ohNf6yFUQv4bI", this)
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

        bPlay = findViewById(R.id.b_play)
        bPlay?.setOnClickListener {
            musicPlaying =
                if (musicPlaying) {
                    mediaPlayer?.pause()
                    player?.pause()
                    bPlay?.setImageResource(R.drawable.play)
                    false
                } else {
                    mediaPlayer?.start()
                    player?.play()
                    bPlay?.setImageResource(R.drawable.stop)
                    true
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
                                mediaPlayer?.start()
                                player?.play()
                                bPlay?.setImageResource(R.drawable.stop)
                                musicPlaying = true
                            }
                            "stop" -> {
                                mediaPlayer?.pause()
                                player?.pause()
                                bPlay?.setImageResource(R.drawable.play)
                                musicPlaying = false
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

                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                        }
                        mediaPlayer?.setDataSource(path)
                        mediaPlayer?.apply {
                            prepareAsync()
                        }

                        songTitle?.text = name

                        songLengthThread = SongLengthThread()
                        songLengthThread!!.start()

                        player?.release()
                        player = null
                    }
                }

                bluetoothDeviceRequestCode ->  {
                    intentData?.let { data ->
                        val address = data.getStringExtra("address")
                        val name = data.getStringExtra("name")

                        deviceStatus?.text = getString(R.string.connecting)
                        val deviceNameText = "Name: $name"
                        deviceName?.text = deviceNameText

                        createConnectThread = CreateConnectThread(bluetoothAdapter!!, address)
                        createConnectThread!!.start()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        createConnectThread?.cancel()
    }

    override fun onInitializationSuccess(p0: YouTubePlayer.Provider?, p1: YouTubePlayer?, p2: Boolean) {
        if(p1 == null) return
        if (p2) {
            p1.pause()
            player = p1
            songTitle?.text = "No song chosen"
            mediaPlayer = null
        }
        else {
            showdialog()
            p1.cueVideo(m_Text)
            p1.setPlayerStyle(YouTubePlayer.PlayerStyle.DEFAULT)
            p1.pause()
            player = p1
            songTitle?.text = "No song chosen"
            mediaPlayer = null
        }
    }

    override fun onInitializationFailure(p0: YouTubePlayer.Provider?, p1: YouTubeInitializationResult) {
        var a = 5
    }

    private fun showdialog(){
        val builder: AlertDialog.Builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Title")

// Set up the input
        val input = EditText(this)
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setHint("Enter Text")
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

// Set up the buttons
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
            // Here you get get input text from the Edittext
            m_Text = input.text.toString()
        })
        builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, which -> dialog.cancel() })

        builder.show()
    }

    private fun millisecondsToMinutes(totalMilliseconds: Int) : String {
        val totalSeconds = totalMilliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return String.format("%02d:%02d", minutes, seconds)
    }

    private inner class SongLengthThread : Thread() {
        override fun run() {
            while (true) {
                val duration = mediaPlayer?.duration ?: 0
                val position = mediaPlayer?.currentPosition ?: 0
                songProgress?.max = duration
                songProgress?.progress = position
                val songLengthText = millisecondsToMinutes(position) + " - " + millisecondsToMinutes(duration)
                songLength?.text = songLengthText
            }
        }
    }

    private inner class YoutubeThread() : Thread(),  YouTubePlayer.OnInitializedListener {
        var youtubePlayer : YouTubePlayer? = null
        var bool: Boolean = true

        override fun run() {
            while (true) {
                if(youtubePlayer == null) return
                if (bool) {
                    try {
                        sleep(1000)
                    } catch (e: InterruptedException) {
                        // Do something here if you need to
                    }
                    youtubePlayer?.seekToMillis(10000)
                    youtubePlayer?.play()
                }
                else {
                    youtubePlayer?.cueVideo("0GXjAMnv1zs")
                    youtubePlayer?.setPlayerStyle(YouTubePlayer.PlayerStyle.DEFAULT)
                    try {
                        sleep(1000)
                    } catch (e: InterruptedException) {
                        // Do something here if you need to
                    }
                    youtubePlayer?.seekToMillis(10000)
                    youtubePlayer?.play()
                }
            }
        }

        override fun onInitializationSuccess(p0: YouTubePlayer.Provider?, p1: YouTubePlayer?, p2: Boolean) {
            youtubePlayer = p1
            bool = p2
            run()
        }

        override fun onInitializationFailure(p0: YouTubePlayer.Provider?, p1: YouTubeInitializationResult) {
            var a = 5
        }
    }

    private inner class CreateConnectThread(bluetoothAdapter: BluetoothAdapter, address: String?) : Thread() {
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

    private inner class ConnectedThread(mmSocket: BluetoothSocket) : Thread() {
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

