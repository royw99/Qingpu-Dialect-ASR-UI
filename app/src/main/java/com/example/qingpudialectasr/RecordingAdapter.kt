package com.example.qingpudialectasr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecordingAdapter(
    private val recordings: List<Recording>,
    private val onItemAction: (Recording, String) -> Unit,
    private val onItemLongClick: (Recording, Int) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    class RecordingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val filenameText: TextView = view.findViewById(R.id.filenameText)
        val durationText: TextView = view.findViewById(R.id.durationText)
        val playButton: Button = view.findViewById(R.id.playButton)
        val itemView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recording = recordings[position]
        
        holder.filenameText.text = recording.fileName
        holder.durationText.text = recording.duration
        
        holder.playButton.setOnClickListener {
            onItemAction(recording, "play")
        }
        
        holder.itemView.setOnLongClickListener {
            onItemLongClick(recording, position)
            true
        }
    }

    override fun getItemCount() = recordings.size
} 