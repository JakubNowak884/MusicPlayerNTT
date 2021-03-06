package com.example.musicplayerntt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import android.text.InputType
import android.util.Log
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
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

/**
 * Starting acitivity of an application.
 *
 * User is able to:
 * select bluetooth device to which he wants connect to,
 * load video from youtube,
 * load song from music directory,
 * rewind a song with a seek bar,
 * start/stop song.
 * The following information is given to the user:
 * name of a bluetooth device,
 * status of connection of a bluetooth device,
 * youtube video,
 * name of a song loaded from music directory,
 * length and current position of a song loaded from music directory or a youtube video.
 */
class MainActivity : AppCompatActivity(), YouTubePlayer.OnInitializedListener {
    /**
     * Information if music is currently playing from song from files or youtube video.
     */
    private var musicPlaying = false
    /**
     * URL to youtube video without "https://youtu.be/" part.
     */
    private var youtubeUrl: String? = null
    /**
     * Title of the song or caption "Youtube video" in a view.
     */
    private var songTitle: TextView? = null
    /**
     * Length and curernt position of a song from file or youtube video in a view.
     */
    private var songLength: TextView? = null
    /**
     * Length and curernt position of a song from file or youtube video in a view as a seek bar.
     */
    private var songProgress: SeekBar? = null
    /**
     * Name of a bluetooth device in a view.
     */
    private var deviceName: TextView? = null
    /**
     * Status of connection of a bluetooth device in a view.
     */
    private var deviceStatus: TextView? = null
    /**
     * Button with image able to stop or start music. Each action changes button image.
     */
    private var bPlay: ImageButton? = null
    /**
     * Plays song from music directory.
     */
    private var mediaPlayer: MediaPlayer? = null
    /**
     * Thread in which length, current position and seek bar of a song is uptated.
     */
    private var songLengthThread: SongLengthThread? = null
    /**
     * Shows youtube video.
     */
    private var youtubeFragment: YouTubePlayerSupportFragment? = null
     /**
     * Plays and stops youtube video.
     */
    private var youtubePlayer : YouTubePlayer? = null
    /**
     * Handler for receiving messages from bluetooth device.
     */
    private var handler: Handler? = null
    /**
     * Socket for bluetooth connection.
     */
    private var mmSocket: BluetoothSocket? = null
    /**
     * Thread receiving input from bluetooth device.
     */
    private var connectedThread: ConnectedThread? = null
    /**
     * Waits for bluetooth connection and creates connected thread.
     */
    private var createConnectionThread: CreateConnectionThread? = null
    /**
     * Adaped for bluetooth connection.
     */
    private var bluetoothAdapter: BluetoothAdapter? = null
    /**
     * Identifies message status in a bluetooth handler.
     */
    private val connectingStatus = 1
    /**
     * Identifies message update in a bluetooth handler.
     */
    private val messageRead = 2
    /**
     * Information given to intent if ListActivity was opened by clicking "load song from files" button.
     */
    private val songRequestCode = 0
    /**
     * Information given to intent if ListActivity was opened by clicking "select device" button.
     */
    private val bluetoothDeviceRequestCode = 1
    
    /**
     * Initializes listeners for controllers and starts (not running) SongLengthThread.
     *
     * @see onCreate.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        songTitle = findViewById(R.id.songTitle)
        songProgress = findViewById(R.id.songSeekBar)
        songLength = findViewById(R.id.songLength)
        deviceName = findViewById(R.id.deviceName)
        deviceStatus = findViewById(R.id.deviceStatus)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        songLengthThread = SongLengthThread()
        songLengthThread!!.start()

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
                showDialog()
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
                    youtubePlayer?.pause()
                    bPlay?.setImageResource(R.drawable.play)
                    false
                } else {
                    mediaPlayer?.start()
                    youtubePlayer?.play()
                    bPlay?.setImageResource(R.drawable.stop)
                    true
                }
        }

        songProgress?.setOnSeekBarChangeListener(object: OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBa: SeekBar) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress * 1000)
                    youtubePlayer?.seekToMillis(progress * 1000)
                }
            }
        })

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
                                youtubePlayer?.play()
                                bPlay?.setImageResource(R.drawable.stop)
                                musicPlaying = true
                            }
                            "stop" -> {
                                mediaPlayer?.pause()
                                youtubePlayer?.pause()
                                bPlay?.setImageResource(R.drawable.play)
                                musicPlaying = false
                            }
                        }
                    }
                }
            }
        }
    }
    /**
     * If activity returns from list of songs initializes media player with chosen song and runs SongLengthThread.
     * If activity returns from list of devices initializes connections with chosen device and starts CreateConnectionThread.
     *
     * @see onActivityResult
     * @see SongLengthThread
     * @see CreateConnectionThread
     */
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

                        songLengthThread?.isRunning = false

                        youtubePlayer?.release()
                        youtubePlayer = null

                        songLengthThread?.isRunning = true
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
    /**
     * Cancels connection with a bluetooth device.
     *
     * @see onDestroy
     */
    override fun onDestroy() {
        super.onDestroy()
        createConnectThread?.cancel()
    }
    /**
     * Loads youtube video from URL and runs SongLengthThread.
     *
     * @see onInitializationSuccess
     * @see SongLengthThread
     */
    override fun onInitializationSuccess(p0: YouTubePlayer.Provider?, p1: YouTubePlayer?, p2: Boolean) {
        if(p1 == null) return
        if (p2) {
            p1.play()
            youtubePlayer = p1
            songTitle?.text = getString(R.string.youtube_video)
            songLengthThread?.isRunning = false

            mediaPlayer?.release()
            mediaPlayer = null

            songLengthThread?.isRunning = true
        }
        else {
            p1.cueVideo(youtubeUrl)
            p1.play()
            youtubePlayer = p1
            songTitle?.text = getString(R.string.youtube_video)
            songLengthThread?.isRunning = false

            mediaPlayer?.release()
            mediaPlayer = null

            songLengthThread?.isRunning = true
        }
    }
    /**
     * @see onInitializationFailure
     */
    override fun onInitializationFailure(p0: YouTubePlayer.Provider?, p1: YouTubeInitializationResult) {}
 /**
     * Shows dialog to enter youtube URL.
     *
     * After clicking "Ok" button youtube URL is assigned to variable and then youtubeFragment is initialized.
     */
    private fun showDialog(){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Youtube URL")

        // Set up the input
        val input = EditText(this)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.hint = "Enter URL"
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("OK") { _, _ ->
            // Here you get get input text from the Edittext
            val inputString = input.text.toString()
            youtubeUrl = inputString.removePrefix("https://youtu.be/")
            if (youtubeUrl == inputString) {
                Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            } else {
                //warning "This cast never succeed" appears because YouTubeAndroidPlayerApi doesn't support Androidx.
                youtubeFragment = supportFragmentManager.findFragmentById(R.id.youtube_fragment) as YouTubePlayerSupportFragment
                //error "Cannot access" appears because YouTubeAndroidPlayerApi doesn't support Androidx.
                //but program still compiles correctly
                youtubeFragment?.initialize(getString(R.string.api_key), this)
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }
    /**
     * Converts seconds to minutes in format "00:00".
     *
     * @param totalSeconds seconds to convert
     * @return minutes as string in format "00:00"
     */
    private fun secondsToMinutes(totalSeconds: Int) : String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return String.format("%02d:%02d", minutes, seconds)
    }
    /**
     * Thread for changing song length text and seek bar.
     * 
     * @see Thread
     */
    private inner class SongLengthThread : Thread() {
        /**
         * Information if threading is currently running.
         */
        var isRunning = false

        /**
         * Updates song length text and seek bar depending whether youtube player or media player is active.
         * 
         * @see Run
         */
        override fun run() {
            while (true) {
                if (isRunning) {
                    var duration = 0
                    var position = 0
                    if (youtubePlayer != null) {
                        duration = youtubePlayer?.durationMillis?.div(1000) ?: 0
                        position = youtubePlayer?.currentTimeMillis?.div(1000) ?: 0
                    }
                    else if (mediaPlayer != null) {
                        duration = mediaPlayer?.duration?.div(1000) ?: 0
                        position = mediaPlayer?.currentPosition?.div(1000) ?: 0
                    }
                    songProgress?.max = duration
                    songProgress?.progress = position
                    val songLengthText = secondsToMinutes(position) + " - " + secondsToMinutes(duration)
                    songLength?.text = songLengthText
                }
            }
        }
    }
    /**
     * Thread for waiting for connection to bluetooth device.
     * 
     * @constructor Creates connection with a bluetooth device.
     * @see Thread
     */
    private inner class CreateConnectThread(bluetoothAdapter: BluetoothAdapter, address: String?) : Thread() {
        /**
        * Tries to connect to a bluetooth device.
        * 
        * @see run
        */
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
        /**
         * Closes connection with a bluetooth device.
         */
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
    /**
     * Thread for receiving input from a bluetooth device and holding connection.
     * 
     * @constructor Creates input stream.
     * @see Thread
     */
    private inner class ConnectedThread(mmSocket: BluetoothSocket) : Thread() {
        /**
         * Input stream from bluetooth device.
         */
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        /**
         * Receives messages from a bluetooth device.
         * 
         * @see Run
         */
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

