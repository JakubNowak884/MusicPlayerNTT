package com.example.musicplayerntt

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Activity to choose name and address from list of object names.
 *
 * Activity is showed after bLoad or bSelect button in MainActivity is clicked. 
 * List of objects is showed to the user. After picking one object user is redirected to the MainActivity.
 */
class ListActivity : AppCompatActivity()  {

    /**
     * Adapter for recycler view.
     */
    private var adapter: CustomAdapter? = null

    /**
     * List of items for recycler view.
     */
    private var listOfNamesAndAddresses: RecyclerView? = null

    /**
     * Creates activity with list of names and addresses in recycler view and show their names.
     *
     * @see onCreate
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val listOfNames = intent.getStringArrayListExtra("listOfNames")
        val listOfAddresses = intent.getStringArrayListExtra("listOfAddresses")

        val namesAndAddresses = ArrayList<Pair<String, String>>()
        for(i in listOfNames!!.indices) {
            namesAndAddresses.add(Pair(listOfNames[i], listOfAddresses!![i]))
        }

        adapter = CustomAdapter { name -> adapterOnClick(name) }
        adapter?.submitList(namesAndAddresses)

        listOfNamesAndAddresses = findViewById(R.id.recyclerView)
        listOfNamesAndAddresses?.layoutManager = LinearLayoutManager(this)
        listOfNamesAndAddresses?.adapter = adapter
    }

    /**
     * Listens if item from recycler view has been clicked.
     *
     * @param nameAndAddress object which name has been clicked.
     */
    private fun adapterOnClick(nameAndAddress: Pair<String, String>) {
        val intent = Intent(this, MainActivity()::class.java)
        intent.putExtra("name", nameAndAddress.first)
        intent.putExtra("address", nameAndAddress.second)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}