package com.parrot.hellodrone

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.parrot.drone.groundsdk.GroundSdk
import com.parrot.drone.groundsdk.ManagedGroundSdk
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.DeviceState
import com.parrot.drone.groundsdk.device.Drone
import com.parrot.drone.groundsdk.device.RemoteControl
import com.parrot.drone.groundsdk.device.instrument.BatteryInfo
import com.parrot.drone.groundsdk.device.peripheral.StreamServer
import com.parrot.drone.groundsdk.device.peripheral.ThermalCamera
import com.parrot.drone.groundsdk.device.peripheral.ThermalControl
import com.parrot.drone.groundsdk.device.peripheral.camera.Camera
import com.parrot.drone.groundsdk.device.peripheral.stream.CameraLive
import com.parrot.drone.groundsdk.device.pilotingitf.Activable
import com.parrot.drone.groundsdk.device.pilotingitf.ManualCopterPilotingItf
import com.parrot.drone.groundsdk.facility.AutoConnection
import com.parrot.drone.groundsdk.stream.GsdkStreamView
import com.parrot.drone.groundsdk.value.EnumSetting
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCamera
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    private lateinit var groundSdk: GroundSdk

    //Drone:
    /** Current drone instance. */
    private var drone: Drone? = null
    /** Reference to the current drone state. */
    private var droneStateRef: Ref<DeviceState>? = null
    /** Reference to the current drone battery info instrument. */
    private var droneBatteryInfoRef: Ref<BatteryInfo>? = null
    /** Reference to a current drone piloting interface. */
    private var pilotingItfRef: Ref<ManualCopterPilotingItf>? = null
    /** Reference to the current drone stream server Peripheral. */
    private var streamServerRef: Ref<StreamServer>? = null
    /** Reference to the current drone live stream. */
    private var liveStreamRef: Ref<CameraLive>? = null
    /** Current drone live stream. */
    private var liveStream: CameraLive? = null
    /** Take pic */
    //private var changeCameraRef: Ref<ThermalCamera>? = null
    


    //Remote control:
    /** Current remote control instance. */
    private var rc: RemoteControl? = null
    /** Reference to the current remote control state. */
    private var rcStateRef: Ref<DeviceState>? = null
    /** Reference to the current remote control battery info instrument. */
    private var rcBatteryInfoRef: Ref<BatteryInfo>? = null

    //Interface:
    /** Video stream view. */
    private lateinit var streamView: GsdkStreamView
    /** Drone state text view. */
    private lateinit var droneStateTxt: TextView
    /** Drone battery level text view. */
    private lateinit var droneBatteryTxt: TextView
    /** Remote state level text view. */
    private lateinit var rcStateTxt: TextView
    /** Remote battery level text view. */
    private lateinit var rcBatteryTxt: TextView
    /** Take off / land button. */
    private lateinit var takeOffLandBt: Button
    /** Take pic button */
    //private lateinit var takepicBt: Button
    private lateinit var downloadFileBt: Button
    private lateinit var uploadFileBt: Button
    /** Switch camera */
    //private lateinit var changeCameraBt: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        streamView = findViewById(R.id.stream_view)
        droneStateTxt = findViewById(R.id.droneStateTxt)
        droneBatteryTxt = findViewById(R.id.droneBatteryTxt)
        rcStateTxt = findViewById(R.id.rcStateTxt)
        rcBatteryTxt = findViewById(R.id.rcBatteryTxt)
        takeOffLandBt = findViewById(R.id.takeOffLandBt)
        downloadFileBt = findViewById(R.id.downloadfileBt)
        uploadFileBt = findViewById(R.id.uploadFileBt)
        //changeCameraBt = findViewById(R.id.changeCameraBt)
        takeOffLandBt.setOnClickListener {onTakeOffLandClick()}
        downloadFileBt.setOnClickListener {downloadFileClick()}
        uploadFileBt.setOnClickListener {uploadFileClick()}
        //changeCameraBt.setOnClickListener {changeCameraClick()}
        //takepicBt.setOnClickListener {takepicClick()}

        droneStateTxt.text = DeviceState.ConnectionState.DISCONNECTED.toString()
        rcStateTxt.text = DeviceState.ConnectionState.DISCONNECTED.toString()

        groundSdk = ManagedGroundSdk.obtainSession(this)

    }

    override fun onStart() {
        super.onStart()

        // Monitor the auto connection facility
        groundSdk.getFacility(AutoConnection::class.java) {

            it?.let{
                if (it.status != AutoConnection.Status.STARTED) {
                    it.start()
                }
                if (drone?.uid != it.drone?.uid) {
                    if(drone != null) {
                        // Stop monitoring the old drone.
                        stopDroneMonitors()

                        // Reset user interface drone part.
                        resetDroneUi()
                    }
                    drone = it.drone
                    if(drone != null) {
                        startDroneMonitors()
                    }
                }

                if (rc?.uid  != it.remoteControl?.uid) {
                    if(rc != null) {
                        stopRcMonitors()

                        resetRcUi()
                    }

                    rc = it.remoteControl
                    if(rc != null) {
                        startRcMonitors()
                    }
                }
            }
        }
    }

    //Reset drone user interface part
    private fun resetDroneUi() {
        // Reset drone user interface views.
        droneStateTxt.text = DeviceState.ConnectionState.DISCONNECTED.toString()
        droneBatteryTxt.text = ""
        takeOffLandBt.isEnabled = false
        // Stop rendering the stream
        streamView.setStream(null)
    }

    //Start drone monitors
    private fun startDroneMonitors() {
        // Monitor drone state.
        monitorDroneState()

        // Monitor drone battery level.
        monitorDroneBatteryLevel()

        // Monitor piloting interface.
        monitorPilotingInterface()

        // Start video stream.
        startVideoStream()
    }

    //Stop drone monitors
    private fun stopDroneMonitors() {

        droneStateRef?.close()
        droneStateRef = null

        droneBatteryInfoRef?.close()
        droneBatteryInfoRef = null

        pilotingItfRef?.close()
        pilotingItfRef = null

        liveStreamRef?.close()
        liveStreamRef = null

        streamServerRef?.close()
        streamServerRef = null

        liveStream = null
    }

    //Start the video stream
    private fun startVideoStream() {
        streamServerRef = drone?.getPeripheral(StreamServer::class.java) { streamServer ->

            if (streamServer != null) {
                if(!streamServer.streamingEnabled()) {
                    streamServer.enableStreaming(true)
                    //sendGet()
                }
                if (liveStreamRef == null) {
                    liveStreamRef = streamServer.live { liveStream ->

                        if (liveStream != null) {
                            if (this.liveStream == null) {
                                streamView.setStream(liveStream)
                            }

                            if (liveStream.playState() != CameraLive.PlayState.PLAYING) {
                                liveStream.play()
                            }
                        } else {
                            streamView.setStream(null)
                        }
                        this.liveStream = liveStream
                    }
                }
            } else {
                liveStreamRef?.close()
                liveStreamRef = null
                streamView.setStream(null)
            }
        }
    }

    //Monitor current drone state
    private fun monitorDroneState() {
        droneStateRef = drone?.getState {
            it?.let {
                droneStateTxt.text = it.connectionState.toString()
            }
        }
    }

    //Monitor current drone battery level
    private fun monitorDroneBatteryLevel() {
        droneBatteryInfoRef = drone?.getInstrument(BatteryInfo::class.java) {
            it?.let {
                droneBatteryTxt.text = getString(R.string.percentage, it.batteryLevel)
            }
        }
    }

    //Monitor current drone piloting interface
    private fun monitorPilotingInterface() {
        pilotingItfRef = drone?.getPilotingItf(ManualCopterPilotingItf::class.java) {
            if (it == null) {
                takeOffLandBt.isEnabled = false
            } else {
                managePilotingItfState(it)
            }
        }
    }

    //Manage piloting interface state
    private fun managePilotingItfState(itf: ManualCopterPilotingItf) {
        when(itf.state) {
            Activable.State.UNAVAILABLE -> {
                takeOffLandBt.isEnabled = false
            }

            Activable.State.IDLE -> {
                takeOffLandBt.isEnabled = false
                itf.activate()
            }

            Activable.State.ACTIVE -> {
                when {
                    itf.canTakeOff() -> {
                        // Drone can take off.
                        takeOffLandBt.isEnabled = true
                        takeOffLandBt.text = getString(R.string.take_off)
                    }
                    itf.canLand() -> {
                        // Drone can land.
                        takeOffLandBt.isEnabled = true
                        takeOffLandBt.text = getString(R.string.land)
                    }
                    else -> // Disable the button.
                        takeOffLandBt.isEnabled = false
                }
            }
        }
    }

    //Take off/land button click
    private fun onTakeOffLandClick() {
        pilotingItfRef?.get()?.let { itf ->
            if (itf.canTakeOff()) {
                // Take off
                itf.takeOff()
            } else if (itf.canLand()) {
                // Land
                itf.land()
                //getJson()
            }
        }
    }

    //Resets remote user interface part
    private fun resetRcUi() {
        rcStateTxt.text = DeviceState.ConnectionState.DISCONNECTED.toString()
        rcBatteryTxt.text = ""
    }

    //Start remote control monitors
    private fun startRcMonitors() {
        monitorRcState()
        monitorRcBatteryLevel()
    }

    //Stop remote control monitors
    private fun stopRcMonitors() {
        rcStateRef?.close()
        rcStateRef = null

        rcBatteryInfoRef?.close()
        rcBatteryInfoRef = null
    }

    //Monitor current remote control state
    private fun monitorRcState() {
        rcStateRef = rc?.getState {
            it?.let {
                rcStateTxt.text = it.connectionState.toString()
            }
        }
    }

    //Monitor current remote control battery level
    private fun monitorRcBatteryLevel() {
        rcBatteryInfoRef = rc?.getInstrument(BatteryInfo::class.java) {

            it?.let {
                rcBatteryTxt.text = getString(R.string.percentage, it.batteryLevel)
            }
        }
    }


    /** ---------------------------------------------------------------------------------- **/
    /** DOWNLOAD AND UPLOAD **/

    private fun downloadFileClick() {
        getJSON()
        Toast.makeText(this@MainActivity, "File downloaded successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun uploadFileClick() {
        Toast.makeText(this@MainActivity, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
        val jsondoprzeslania = "{\"Link\": \"https://ankieta.asuscomm.com/~ola/uploads/zdjecie.jpg\",\"Date\": \"2020-11-12T00:00:00\",\"Secret\": \"superProjekt\"}"
        println(jsondoprzeslania)

        postJSON("http://ankieta.asuscomm.com:5010/api/todoitems", jsondoprzeslania)
        UploadUtility(this@MainActivity).uploadFile("/storage/emulated/0/Download/zdjecie.jpg", "https://ankieta.asuscomm.com/~ola/uploads/zdjecie.jpg")
    }

    private fun getJSON() {
        val thread = Thread(Runnable {
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                    .url("http://192.168.42.1/api/v1/media/medias")
                    //.url("http://fizyka.umk.pl/~291605/naszybko/medias.json")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val data = response?.body?.string()
                val gson = GsonBuilder().create()
                //val dane = gson.fromJson(data, Data::class.java)
                val collectionType: Type =
                        object : TypeToken<ArrayList<Data?>?>(){}.type
                val dane: ArrayList<Data> = gson.fromJson(data, collectionType)
                dane.toList()

                var resulttext = ""

                val x = ""
                //for(i in 0 until dane.length())
                val urltodownload = "http://192.168.42.1/"+dane[0].thumbnail
                resulttext = "Thumbnail:"+urltodownload

                println(resulttext).toString()
                println(urltodownload)

            }
        })
        thread.start()
        val thread2 = Thread(Runnable {
            println("POBIERANIEee")
            //download("/storage/emulated/0/Download/ahA.mp4", "http://fizyka.umk.pl/~291605/naszybko/new_rl_op.mp4")
            //download("/storage/emulated/0/Download/medias.json", "https://www.w3.org/TR/PNG/iso_8859-1.txt")
            download("/storage/emulated/0/Download/100000130013.mp4", "http://192.168.42.1/data/media/100000130013.MP4")
            //download("/storage/emulated/0/Download/100000130013.mp4", "http://192.168.42.1/data/media/100000130013.MP4")
            //download("/storage/emulated/0/Download/dron2.mp4", "http://192.168.42.1/data/media/100000090009.MP4")
            println("POBRANOoo")
        })
        thread2.start()
    }

    fun postJSON(url: String, json: String){
        var result = ""
        val thread = Thread(Runnable {
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                    .url(url)
                    .post(json.toRequestBody(mediaType))
                    .build()
            client.newCall(request).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("No connection with database")
                }
                override fun onResponse(call: Call, response: Response) {
                    if(!response.isSuccessful) throw IOException("Unexpected code $response")
                    val data = response?.body?.string()
                    val obj: JsonObject = Gson().fromJson(data, JsonObject::class.java)
                    //result = obj["message"].asString
                    //println("WYNIKKK: " + result);
                }
            })
        })
        thread.run()
        println("znowu dziala hihi")
    }

    fun download(path: String, link: String) {
        URL(link).openStream().use { input ->
            FileOutputStream(File(path)).use { output ->
                println("POBIERANIE")
                input.copyTo(output)
                println("KONIEC")
            }
        }
    }

//    fun upload(){
//
//        if (isExternalStorageWritable()) {
//            FileOutputStream(fileName).use { output ->
//                output.write(fileBody.toByteArray())
//            }
//        }
//        if (isExternalStorageReadable()) {
//            FileInputStream(fileName).use { stream ->
//                val text = stream.bufferedReader().use {
//                    it.readText()
//                }
//                Log.d("TAG", "LOADED: $text")
//            }
//        }
//    }
//
//    fun isExternalStorageWritable(): Boolean {
//        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
//    }
//
//    fun isExternalStorageReadable(): Boolean {
//        return Environment.getExternalStorageState() in
//                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
//    }

//    private fun storeFileInInternalStorage(selectedFile: File, internalStorageFileName: String) {
//        val inputStream = FileInputStream(selectedFile) // 1
//        val outputStream = application.openFileOutput(internalStorageFileName, Context.MODE_PRIVATE)  // 2
//        val buffer = ByteArray(1024)
//        inputStream.use {  // 3
//            while (true) {
//                val byeCount = it.read(buffer)  // 4
//                if (byeCount < 0) break
//                outputStream.write(buffer, 0, byeCount)  // 5
//            }
//            outputStream.close()  // 6
//        }
//    }
//    fun readFile() {
//        val thread3 = Thread(Runnable {
//            println("wczytuję plik")
//            val selectedVideoFilePath =
//                "/storage/emulated/0/Download/medias.json"  // 1
//            val selectedVideoFile : File = File(selectedVideoFilePath)  // 2
//            val selectedVideoFileExtension : String = selectedVideoFile.extension  // 3
//            val internalStorageVideoFileName : String =
//                UUID.randomUUID().toString().plus(selectedVideoFileExtension)  // 4
//            storeFileInInternalStorage(selectedVideoFile, internalStorageVideoFileName)  // 5
//            println("wczytano plik")
//            println(selectedVideoFile)
//            println(internalStorageVideoFileName)
//            println(selectedVideoFileExtension)
//            println(selectedVideoFilePath)
//        })
//        thread3.start()
//    }


    /**-------------------------------------------------**/
    /** CAMERA **/
    /**-------------------------------------------------**/

    private fun changeCameraClick() {
        //drone.getPeripheral(ThermalControl.Mode.STANDARD)
        //EnumSetting<ThermalControl.Mode> =
        //ThermalControl.Mode.STANDARD;

        //drone?.getPeripheral(ThermalCamera)

//                changeCameraRef?.get()?.let { itf ->
//            if (itf.()) {
//                itf.takeOff()
//            } else if (itf.canLand()) {
//                // Land
//                itf.land()
//                //getJson()
//            }
//        }
    }
}
