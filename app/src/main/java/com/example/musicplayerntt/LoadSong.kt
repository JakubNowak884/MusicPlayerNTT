package com.example.musicplayerntt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.*
import java.io.File

class LoadSong : AppCompatActivity() {

    val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123

    private lateinit var adapter: CustomAdapter
    private lateinit var listOfSongs: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_load_song)

        var array = arrayListOf<File>()

        if (checkPermissionREAD_EXTERNAL_STORAGE(this)) {

            val musicDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            var allFiles = musicDirectory.listFiles().toList()

            for (file: File in allFiles)
            {
                array.add(file)
            }
        }

        adapter = CustomAdapter() { file -> adapterOnClick(file) }
        adapter.submitList(array)
        listOfSongs = findViewById(R.id.recyclerview)
        listOfSongs.layoutManager = LinearLayoutManager(this)
        listOfSongs.adapter = adapter
    }

    private fun adapterOnClick(file: File) {
        val intent = Intent(this, MainActivity()::class.java)
        intent.putExtra("absolutePath", file.absolutePath)
        intent.putExtra("name", file.name)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun checkPermissionREAD_EXTERNAL_STORAGE(
        context: Context?
    ): Boolean {
        val currentAPIVersion = Build.VERSION.SDK_INT
        return if (currentAPIVersion >= Build.VERSION_CODES.M) {
            if (context?.let {
                    ContextCompat.checkSelfPermission(
                        it,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                } != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        (context as Activity?)!!,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                ) {
                    showDialog(
                        "External storage", context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                } else {
                    ActivityCompat
                        .requestPermissions(
                            (context as Activity?)!!,
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
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

class CustomAdapter(private val onClick: (File) -> Unit) :
    ListAdapter<File, CustomAdapter.FlowerViewHolder>(FlowerDiffCallback) {

    /* ViewHolder for Flower, takes in the inflated view and the onClick behavior. */
    class FlowerViewHolder(itemView: View, val onClick: (File) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val flowerTextView: TextView = itemView.findViewById(R.id.textVieww)
        private var currentFile: File? = null

        init {
            itemView.setOnClickListener {
                currentFile?.let {
                    onClick(it)
                }
            }
        }

        /* Bind flower name and image. */
        fun bind(file: File) {
            currentFile = file

            flowerTextView.text = file.name
        }
    }

    /* Creates and inflates view and return FlowerViewHolder. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlowerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.load_song_design, parent, false)
        return FlowerViewHolder(view, onClick)
    }

    /* Gets current flower and uses it to bind view. */
    override fun onBindViewHolder(holder: FlowerViewHolder, position: Int) {
        val flower = getItem(position)
        holder.bind(flower)
    }
}

object FlowerDiffCallback : DiffUtil.ItemCallback<File>() {
    override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.name == newItem.name
    }
}