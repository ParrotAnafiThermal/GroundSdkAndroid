package com.parrot.hellodrone

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.parrot.drone.groundsdk.GroundSdk
import com.parrot.drone.groundsdk.ManagedGroundSdk
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.DeviceState
import com.parrot.drone.groundsdk.device.Drone
import com.parrot.drone.groundsdk.device.RemoteControl
import com.parrot.drone.groundsdk.device.instrument.BatteryInfo
import com.parrot.drone.groundsdk.device.peripheral.MainCamera
import com.parrot.drone.groundsdk.device.peripheral.StreamServer
import com.parrot.drone.groundsdk.device.peripheral.stream.CameraLive
import com.parrot.drone.groundsdk.device.pilotingitf.Activable
import com.parrot.drone.groundsdk.device.pilotingitf.ManualCopterPilotingItf
import com.parrot.drone.groundsdk.facility.AutoConnection
import com.parrot.drone.groundsdk.stream.GsdkStreamView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL


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
    private var takepicRef: Ref<MainCamera>? = null

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
    private lateinit var takepicBt: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        streamView = findViewById(R.id.stream_view)
        droneStateTxt = findViewById(R.id.droneStateTxt)
        droneBatteryTxt = findViewById(R.id.droneBatteryTxt)
        rcStateTxt = findViewById(R.id.rcStateTxt)
        rcBatteryTxt = findViewById(R.id.rcBatteryTxt)
        takeOffLandBt = findViewById(R.id.takeOffLandBt)
        takepicBt = findViewById(R.id.uploadmedia)
        takeOffLandBt.setOnClickListener {onTakeOffLandClick()}
        takepicBt.setOnClickListener {takepicClick()}


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
                   // sendGet()
                    val url = "http://192.168.42.1/api/v1/media/medias"

                    println("hah")
                    //doInBackground()
                    //getJson()
                    println("hah2")
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

//    private class MainCameraRefRunnable:Runnable {
//        private val mainCameraRef:Ref<MainCamera> = null
//        internal fun setMainCameraRef(mainCameraRef:Ref<MainCamera>) {
//            this.mainCameraRef = mainCameraRef
//        }
//        public override fun run() {
//            if (mainCameraRef != null) mainCameraRef.close()
//        }
//    }

    private fun takepicClick() {
        takepicRef?.get()?.let { pic ->
        //upload()
//            if (pic.canStartPhotoCapture()) {
//                pic.startPhotoCapture()
//            } else if (pic.canStopPhotoCapture()) {
//                pic.stopPhotoCapture()
//            }
            getJson()
        }
    }

    /**
    cameraImage.setOnClickListener({ view->
        val camera = ardrone.getMainCamera()
        if (camera == null) return@cameraImage.setOnClickListener
        if (camera.mode().getAvailableValues().contains(MainCamera.Mode.PHOTO))
        {
            val takePhotoRunnable = MainCameraRefRunnable()
            takePhotoRunnable.setMainCameraRef(ardrone.getPeripheral(MainCamera::class.java, object:Ref.Observer<MainCamera>() {
                private val posted:Boolean = false
                fun onChanged(@Nullable cam:MainCamera) {
                    if (cam != null && cam.mode().getValue() === MainCamera.Mode.PHOTO && !posted)
                    {
                        if (cam.canStartPhotoCapture())
                        {
                            posted = true
                            cam.startPhotoCapture()
                            Handler().post(takePhotoRunnable)
                        }
                        else if (cam.canStopPhotoCapture())
                        {
                            posted = true
                            cam.stopPhotoCapture()
                            Handler().post(takePhotoRunnable)
                        }
                    }
                }
            }))
            if (camera.mode().getValue() !== MainCamera.Mode.PHOTO)
                camera.mode().setValue(MainCamera.Mode.PHOTO)
        } })
*/

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



//    private fun doInBackground(): String {
//        var text: String
//        text = ""
//        var listdata: List<Data>
//        val thread = Thread(Runnable {
//            try {
//                val connection = URL("http://192.168.42.1/api/v1/media/medias").openConnection() as HttpURLConnection
//                try{
//                    connection.connect()
//                    text = connection.inputStream.use {it.reader().use{reader -> reader.readText()}}
//
//                }
//                finally {
//                    connection.disconnect()
//                }
//                //handleJson(result)
//                println(text)
//                           }
//                catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//        )
//
//        thread.start()
//        listdata = handleJson(text)
//
//        return text
//
//    }

//    private fun upload(){
//        val link = "https://ankieta.asuscomm.com/~ola/uploads/nazwaWyslanegoPliku"
//        val date = "2019-07-26T00:00:00"
//        val secret = "superProjekt"
//        uploadMedia(link, date, secret)
//    }
//
    private fun getJson() {
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


//                println(response.body?.string())
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
        //download("/storage/emulated/0/Download/kot3.mp4", "http://fizyka.umk.pl/~291605/naszybko/new_rl_op.mp4")
        //download("/storage/emulated/0/Download/img.", "https://static.toiimg.com/photo/msid-67586673/67586673.jpg?3918697")
        //download("/storage/emulated/0/Download/dron.mp4", "http://192.168.42.1/data/media/100000090009.MP4")
        download("/storage/emulated/0/Download/dron2.mp4", "http://192.168.42.1/data/media/100000090009.MP4")
        println("POBRANOoo")
    })
    thread2.start()
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


//    private fun handleJson(jsonString: String): List<Data> {
//        val jsonArray = JSONArray(jsonString)
//        val list = ArrayList<Data>()
//        val list2 = ArrayList<Resources>()
//
//        var x = 0
//        var y = 0
//        while(x < jsonArray.length()) {
//
//            val jsonObject = jsonArray.getJSONObject(x)
//            val temp = jsonObject.getJSONArray("resources")
//
//            while(y < temp.length()){
//                val temp2 = temp.getJSONObject(y)
//
//                list2.add(Resources(
//                    temp2.getString("media_id"),
//                    temp2.getString("resource_id"),
//                    temp2.getString("type"),
//                    temp2.getString("format"),
//                    temp2.getString("datetime"),
//                    temp2.getInt("size"),
//                    temp2.getString("url"),
//                    temp2.getString("storage"),
//                    temp2.getInt("width"),
//                    temp2.getInt("height"),
//                    temp2.getString("thumbnail"),
//                    temp2.getString("video_mode"),
//                    temp2.getString("replay_url"),
//                    temp2.getInt("duration")
//                ))
//            }
//
//            list.add(Data(
//                jsonObject.getString("media_id"),
//                jsonObject.getString("type"),
//                jsonObject.getString("datetime"),
//                jsonObject.getInt("size"),
//                jsonObject.getString("video_mode"),
//                jsonObject.getInt("duration"),
//                jsonObject.getString("run_id"),
//                jsonObject.getString("thumbnail"),
//                list2
//            ))
//
//            x++
//        }
//        println(list.first().resources.first().url)
//        return list
//    }

//    fun sendGet() {
//        val response = try {
//                    println("DZIALA1")
//
//            URL("http://google.co.uk")
//                .openStream()
//                .bufferedReader()
//                .use { it.readText() }
//
//        }
//        finally {}
//                println("DZIALA2")
//
//        println(response)
//        println("DZIALA")
//        with(url.openConnection() as HttpURLConnection) {
//            requestMethod = "GET"  // optional default is GET
//
//            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
//
//
//            inputStream.bufferedReader().use {
//                it.lines().forEach { line ->
//                    println(line)
//                }
//            }
//        }
//    }


//    inner class AsyncTaskHandleJson : AsyncTask<String, String, String>() {
//        override fun doInBackground(vararg url: String?): String {
//            var text: String
//            val connection = URL(url[0]).openConnection() as HttpURLConnection
//            try{
//                connection.connect()
//                text = connection.inputStream.use {it.reader().use{reader -> reader.readText()}}
//
//            }
//            finally {
//                connection.disconnect()
//            }
//            println(text)
//            return text
//        }
//
//        override fun onPostExecute(result: String?) {
//            super.onPostExecute(result)
//            handleJson(result)
//
//        }
//
//}
//
//    inner class AsyncTaskHandleJson {
//         fun doInBackground(url: String?): String {
//            var text: String
//            val connection = URL(url).openConnection() as HttpURLConnection
//            try{
//                connection.connect()
//                text = connection.inputStream.use {it.reader().use{reader -> reader.readText()}}
//
//            }
//            finally {
//                connection.disconnect()
//            }
//            // handleJson(result)
//            println(text)
//            return text
//
//        }

//        override fun onPostExecute(result: String?) {
//            super.onPostExecute(result)
//            handleJson(result)
//
//        }

//    }




}
