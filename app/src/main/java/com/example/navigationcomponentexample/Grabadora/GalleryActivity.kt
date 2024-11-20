package com.example.navigationcomponentexample.Grabadora

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.navigationcomponentexample.databinding.ActivityGalleryBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GalleryActivity : AppCompatActivity(), OnItemClickListener {

    private lateinit var records : ArrayList<AudioRecord>
    private lateinit var mAdapter: Adapter
    private lateinit var db : AppDatabase
    private lateinit var binding: ActivityGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        records = ArrayList()

        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "audioRecords"
        ).build()

        mAdapter = Adapter(records, this)

        binding.recyclerview.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
        }

        fetchAll()
    }

    private fun fetchAll(){
        GlobalScope.launch {
            records.clear()
            var queryResult = db.audioRecordDao().getAll()
            records.addAll(queryResult)

            mAdapter.notifyDataSetChanged()
        }
    }

    override fun onItemClickListener(position: Int) {
        var audioRecord = records[position]
        var intent = Intent(this, AudioPlayerActivity::class.java)

        intent.putExtra("filepath", audioRecord.filepath)
        intent.putExtra("filename", audioRecord.filename)
        startActivity(intent)
    }

    override fun onItemLongClickListener(position: Int) {
        Toast.makeText(this,"Long click", Toast.LENGTH_SHORT).show()
    }
}
