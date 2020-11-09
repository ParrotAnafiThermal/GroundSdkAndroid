package com.parrot.hellodrone

import com.google.gson.annotations.SerializedName

//data class Data(val media_id: String, val type: String, val datetime: String,
//                val size: Int, val video_mode: String, val duration: Int,
//                val run_id: String, val thumbnail: String, val resources: List<Resources>)
//
//data class Resources(val media_id: String, val resource_id: String, val type: String,
//                     val format: String, val datetime: String, val size: Int,
//                     val url: String, val storage: String, val width: Int,
//                     val height: Int, val thumbnail: String, val video_mode: String,
//                     val replay_url: String, val duration: Int)

data class Data(
    @field:SerializedName("media_id") val media_id: String,
    @field:SerializedName("type") val type: String,
    @field:SerializedName("datetime") val datetime: String,
    @field:SerializedName("size") val size: Int,
    @field:SerializedName("video_mode") val video_mode: String,
    @field:SerializedName("duration") val duration: Int,
    @field:SerializedName("run_id") val run_id: String,
    @field:SerializedName("thumbnail") val thumbnail: String,
    @field:SerializedName("resources") val resources: List<Resources>)

data class Resources(
    @field:SerializedName("media_id") val media_id: String,
    @field:SerializedName("resource_id") val resource_id: String,
    @field:SerializedName("type") val type: String,
    @field:SerializedName("format") val format: String,
    @field:SerializedName("datetime") val datetime: String,
    @field:SerializedName("size") val size: Int,
    @field:SerializedName("url") val url: String,
    @field:SerializedName("storage") val storage: String,
    @field:SerializedName("width") val width: Int,
    @field:SerializedName("height") val height: Int,
    @field:SerializedName("thumbnail") val thumbnail: String,
    @field:SerializedName("video_mode") val video_mode: String,
    @field:SerializedName("replay_url") val replay_url: String,
    @field:SerializedName("duration") val duration: Int)