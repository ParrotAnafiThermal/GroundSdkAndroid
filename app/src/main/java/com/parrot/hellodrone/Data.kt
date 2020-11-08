package com.parrot.hellodrone

data class Data(val media_id: String, val type: String, val datetime: String,
                val size: Int, val video_mode: String, val duration: Int,
                val run_id: String, val thumbnail: String, val resources: List<Resources>)

data class Resources(val media_id: String, val resource_id: String, val type: String,
                     val format: String, val datetime: String, val size: Int,
                     val url: String, val storage: String, val width: Int,
                     val height: Int, val thumbnail: String, val video_mode: String,
                     val replay_url: String, val duration: Int)