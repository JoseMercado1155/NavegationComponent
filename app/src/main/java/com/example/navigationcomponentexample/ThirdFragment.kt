package com.example.navigationcomponentexample

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.navigationcomponentexample.Camara_Video.appSettingOpen
import com.example.navigationcomponentexample.Camara_Video.gone
import com.example.navigationcomponentexample.Camara_Video.visible
import com.example.navigationcomponentexample.Camara_Video.warningPermissionDialog
import com.example.navigationcomponentexample.databinding.FragmentThirdBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ThirdFragment : Fragment() {

    private var _binding: FragmentThirdBinding? = null
    private val binding get() = _binding!!

    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
            )
    } else {
        arrayListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null

    private var isPhoto = true

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector
    private var orientationEventListener : OrientationEventListener? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var aspectRatio = AspectRatio.RATIO_16_9

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentThirdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Verificar permisos y empezar la cámara
        if (checkMultiplePermission()) {
            startCamera()
        }

        // Configurar los botones
        binding.flipcameraIB.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            bindCameraUserCases()
        }

        binding.aspectRatioTxt.setOnClickListener{
            if (aspectRatio == AspectRatio.RATIO_16_9){
                aspectRatio = AspectRatio.RATIO_4_3
                setAspectRatio("H,4:3")
                binding.aspectRatioTxt.text = "4:3"
            }else{
                aspectRatio = AspectRatio.RATIO_16_9
                setAspectRatio("H,0:0")
                binding.aspectRatioTxt.text = "16:9"
            }
            bindCameraUserCases()
        }

        binding.changecameraToVideoIB.setOnClickListener{
            isPhoto = !isPhoto
            if (isPhoto){
                binding.changecameraToVideoIB.setImageResource(R.drawable.ic_photo)
                binding.captureIB.setImageResource(R.drawable.camera)
            }else{
                binding.changecameraToVideoIB.setImageResource(R.drawable.ic_videocam)
                binding.captureIB.setImageResource(R.drawable.ic_start)
            }

        }

        binding.captureIB.setOnClickListener {
            if (isPhoto){
                takePhoto()
            }else{
                captureVideo()
            }
        }

        binding.flashToggleIB.setOnClickListener {
            setFlashIcon(camera)
        }
    }

    // Verificar permisos necesarios para cámara y almacenamiento
    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireContext() as Activity, listPermissionNeeded.toTypedArray(), multiplePermissionId)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == multiplePermissionId) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                var someDenied = false
                for (permission in permissions) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(requireContext() as Activity, permission)) {
                        if (ActivityCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_DENIED) {
                            someDenied = true
                        }
                    }
                }
                if (someDenied) {
                    appSettingOpen(requireContext())
                } else {
                    warningPermissionDialog(requireContext()) { _, _ -> checkMultiplePermission() }
                }
            }
        }
    }

    // Iniciar la cámara
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    // Vincular las cámaras y los casos de uso (Preview y ImageCapture)
    private fun bindCameraUserCases() {

        val rotation = binding.previewView.display.rotation

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    aspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .setAspectRatio(aspectRatio)
            .build()

        videoCapture = VideoCapture.withOutput(recorder).apply {
            targetRotation = rotation
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation : Int) {

                val myRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = myRotation
                videoCapture.targetRotation = myRotation

            }
        }
        orientationEventListener?.enable()

        try {
            cameraProvider.unbindAll() // Desvincula cualquier cámara previamente conectada
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, videoCapture
            )
            setUpZoomTapToFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setUpZoomTapToFocus(){
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio   ?: 1f
                val delta = detector.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(requireContext(),listener)

        binding.previewView.setOnTouchListener(){ view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN){
                val factory = binding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2,TimeUnit.SECONDS)
                    .build()

                val x = event.x
                val y = event.y

                val focusCircle = RectF(x-50,y-50,x+50,y+50)

                binding.focusCircleView.focusCircle = focusCircle
                binding.focusCircleView.invalidate()

                camera.cameraControl.startFocusAndMetering(action)

                view.performClick()
            }
            true
        }
    }

    // Establecer el icono de flash
    private fun setFlashIcon(camera: Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value == 0) {
                camera.cameraControl.enableTorch(true)
                binding.flashToggleIB.setImageResource(R.drawable.flash_on)
            } else {
                camera.cameraControl.enableTorch(false)
                binding.flashToggleIB.setImageResource(R.drawable.flash_off)
            }
        } else {
            Toast.makeText(requireContext(), "Flash no está disponible", Toast.LENGTH_LONG).show()
            binding.flashToggleIB.isEnabled = false
        }
    }

    // Tomar la foto
    private fun takePhoto() {
        // Desactivar flash temporalmente
        camera.cameraControl.enableTorch(false)

        // Agregar un retraso (por ejemplo, 100ms)
        Handler(Looper.getMainLooper()).postDelayed({
            // Proceder con la captura de la foto
            val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(System.currentTimeMillis()) + ".jpg"

            // Crear archivo y metadata
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Imagenes")
                }
            }

            val metadata = ImageCapture.Metadata().apply {
                isReversedHorizontal = (lensFacing == CameraSelector.LENS_FACING_FRONT)
            }

            val outputOption = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ImageCapture.OutputFileOptions.Builder(
                    requireContext().contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).setMetadata(metadata).build()
            } else {
                val imageFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Imagenes")
                if (!imageFolder.exists()) {
                    imageFolder.mkdirs()
                }
                val imageFile = File(imageFolder, fileName)
                ImageCapture.OutputFileOptions.Builder(imageFile).setMetadata(metadata).build()
            }

            // Tomar la foto
            imageCapture.takePicture(
                outputOption,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Toast.makeText(requireContext(), "Foto guardada en la galeria", Toast.LENGTH_SHORT).show()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(requireContext(), "Error al tomar la foto: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }, 100) // Retraso de 100 ms antes de tomar la foto
    }



    private fun setAspectRatio(ratio:String){
        binding.previewView.layoutParams = binding.previewView.layoutParams.apply {
            if (this is ConstraintLayout.LayoutParams){
                dimensionRatio = ratio
            }
        }
    }

    private fun captureVideo() {

        binding.captureIB.isEnabled = false

        binding.flashToggleIB.gone()
        binding.flipcameraIB.gone()
        binding.aspectRatioTxt.gone()
        binding.changecameraToVideoIB.gone()

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            stopRecording()
            recording = null
            return
        }

        startRecording()

        // Crear un nombre de archivo único basado en la fecha y hora actual
        val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(System.currentTimeMillis()) + ".mp4"

        // Usar MediaStore para guardar el video en la galería
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)  // Nombre del archivo
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")  // Tipo de archivo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Videos")  // Carpeta en la galería
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(requireContext().contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(requireContext(), mediaStoreOutputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d("Captura de video", "Grabación comenzada")
                        binding.captureIB.setImageResource(R.drawable.ic_stop)
                        binding.captureIB.isEnabled = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val message = "Video guardado en la galeria: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        } else {
                            recording?.close()
                            recording = null
                            Log.d("Captura de video", "Error durante la grabación: ${recordEvent.error}")
                        }
                        binding.captureIB.setImageResource(R.drawable.ic_start)
                        binding.captureIB.isEnabled = true

                        // Volver a mostrar los botones de control de la cámara
                        binding.flashToggleIB.visible()
                        binding.flipcameraIB.visible()
                        binding.aspectRatioTxt.visible()
                        binding.changecameraToVideoIB.visible()
                    }
                    else -> {
                        Log.d("Captura de video", "Evento no controlado: $recordEvent")
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
    }

    override fun onPause() {
        super.onPause()
        orientationEventListener?.disable()
        if (recording != null){
            recording?.stop()
            captureVideo()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateTimer = object : Runnable{
        override fun run() {
            val currentTime = SystemClock.elapsedRealtime() - binding.recordingTimerC.base
            val timeString = currentTime.toFormattedTime()
            binding.recordingTimerC.text = timeString
            handler.postDelayed(this,1000)
        }
    }

    private fun Long.toFormattedTime():String{
        val seconds = ((this / 1000) % 60).toInt()
        val minutes = ((this / (1000 * 60)) % 60).toInt()
        val hours = ((this / (1000 * 60 * 60)) % 24).toInt()

        return if (hours >0){
            String.format("%02d:%02d:%02d",hours,minutes,seconds)
        }else{
            String.format("%02d:%02d",minutes,seconds)
        }
    }

    private fun startRecording(){
        binding.recordingTimerC.visible()
        binding.recordingTimerC.base = SystemClock.elapsedRealtime()
        binding.recordingTimerC.start()
        handler.post(updateTimer)
    }

    private fun stopRecording(){
        binding.recordingTimerC.gone()
        binding.recordingTimerC.stop()
        handler.removeCallbacks(updateTimer)
    }

}