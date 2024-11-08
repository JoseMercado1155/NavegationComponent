package com.example.navigationcomponentexample

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.navArgs
import androidx.room.Room
import com.example.navigationcomponentexample.databinding.FragmentSecondBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Date


const val REQUEST_CODE = 200

class SecondFragment : Fragment(),Timer.OnTimerTickListener{

    private lateinit var amplitudes: ArrayList<Float>
    private var permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var permissionGranted = false

    private lateinit var recorder: MediaRecorder
    private var dirPath = ""
    private var fileName = ""
    private var isRecording = false
    private var isPaused = false

    private var duration = ""

    private  lateinit var vibrator: Vibrator

    private lateinit var timer:Timer

    private lateinit var db : AppDatabase

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    val args: SecondFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root // Retorna la vista de binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val name = args.name
        binding.tvTimer.text = name // Usa binding para acceder al TextView

        // Verificar permiso
        permissionGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            permissions[0]
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            setupRecordingButton()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), permissions, REQUEST_CODE)
        }

        db = Room.databaseBuilder(
            requireContext(),
            AppDatabase::class.java,
            "audioRecords"
        ).build()

        val bottomSheet =view.findViewById<View>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED


        timer =Timer(this)
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun setupRecordingButton() {
        binding.btnRecord.setOnClickListener { // Usa binding para acceder al botón
            when{
                isPaused -> resumeRecorder()
                isRecording -> pauseRecorder()
                else -> startRecording()
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        binding.btnList.setOnClickListener{

            startActivity(Intent(requireContext(), GalleryActivity::class.java))
        }

        binding.btnDone.setOnClickListener{
            stopRecorder()
            Toast.makeText(requireContext(), "Grabacion guardada", Toast.LENGTH_SHORT).show()

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.bottomSheetBG.visibility = View.VISIBLE
            val filenameInput = view?.findViewById<TextView>(R.id.filenameInput)
            filenameInput?.setText(fileName)
            filenameInput?.let { hideKeyboard(it) }

        }

        val btnCancel = view?.findViewById<TextView>(R.id.btnCancel)
        btnCancel?.setOnClickListener{
            File("$dirPath$fileName.mp3")
            dismiss()

        }

        val btnOK = view?.findViewById<TextView>(R.id.btnOk)
        btnOK?.setOnClickListener{
            dismiss()
            save()
        }

        binding.bottomSheetBG.setOnClickListener(){
            File("$dirPath$fileName.mp3")
            dismiss()
        }

        binding.btnDelete.setOnClickListener{
            stopRecorder()
            File("$dirPath$fileName.mp3")
            Toast.makeText(requireContext(), "Grabacion eliminada", Toast.LENGTH_SHORT).show()
        }

        binding.btnDelete.isClickable = false

    }

    private fun save() {
        val newFileNameInput = filenameInput?.text.toString()
        val uniqueFileName = if (newFileNameInput.isNotEmpty()) newFileNameInput else fileName

        // Generar un nuevo archivo de audio si el nombre ingresado es diferente al original
        if (uniqueFileName != fileName) {
            val newFile = File("$dirPath$uniqueFileName.mp3")
            File("$dirPath$fileName.mp3").renameTo(newFile)
            fileName = uniqueFileName // Actualizamos el nombre del archivo
        }

        // Generar un filepath único para la grabación
        val filePath = "$dirPath$fileName.mp3"
        val timestamp = Date().time
        val ampsPath = "$dirPath$fileName"

        // Guardar las amplitudes en un archivo único
        try {
            val fos = FileOutputStream(ampsPath)
            val out = ObjectOutputStream(fos)
            out.writeObject(amplitudes)
            fos.close()
            out.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Crear un nuevo registro de la grabación en la base de datos con todos los datos
        val record = AudioRecord(fileName, filePath, timestamp, duration, ampsPath)

        // Insertar el registro de audio en la base de datos
        GlobalScope.launch {
            db.audioRecordDao().insert(record)
        }

        // Iniciar la actividad para reproducir el audio recién guardado
        val intent = Intent(requireContext(), AudioPlayerActivity::class.java)
        intent.putExtra("filepath", filePath) // Pasamos el filepath al Intent
        startActivity(intent) // Inicia AudioPlayerActivity
    }



    val filenameInput = view?.findViewById<TextView>(R.id.filenameInput)
    private fun dismiss() {
        binding.bottomSheetBG.visibility = View.GONE
        filenameInput?.let { hideKeyboard(it) }

        Handler(Looper.getMainLooper()).postDelayed({
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }, 100)
    }

    private fun hideKeyboard(view: View){
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            permissionGranted =
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (permissionGranted) {
                setupRecordingButton()
            }
        }
    }

    private fun pauseRecorder(){
        recorder.pause()
        isPaused = true
        binding.btnRecord.setImageResource(R.drawable.ic_record)

        timer.pause()
    }

    private fun resumeRecorder(){
        recorder.resume()
        isPaused = false
        binding.btnRecord.setImageResource(R.drawable.ic_pause)

        timer.start()
    }

    private fun startRecording() {
        if (!permissionGranted) {
            ActivityCompat.requestPermissions(requireActivity(), permissions, REQUEST_CODE)
            return
        }
        recorder = MediaRecorder()
        dirPath = "${requireContext().externalCacheDir?.absolutePath}/"

        var simpleDateFormat = SimpleDateFormat("yyyy.MM.DD_hh.mm.ss")
        var date = simpleDateFormat.format(Date())
        fileName = "audio_record_$date"

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile("$dirPath$fileName.mp3")

            try {
                prepare()
            }catch (e: IOException){}

            start()
        }

        binding.btnRecord.setImageResource(R.drawable.ic_pause)
        isRecording = true
        isPaused = false

        timer.start()

        binding.btnDelete.isClickable = true
        binding.btnDelete.setImageResource(R.drawable.ic_delete)

        binding.btnList.visibility = View.GONE
        binding.btnDone.visibility = View.VISIBLE
    }
    private fun stopRecorder(){
        timer.stop()

        recorder.apply {
            stop()
            release()
        }

        isPaused = false
        isRecording = false

        binding.btnList.visibility = View.VISIBLE
        binding.btnDone.visibility = View.GONE

        binding.btnDelete.isClickable = false
        binding.btnDelete.setImageResource(R.drawable.ic_delete_disabled)

        binding.btnRecord.setImageResource(R.drawable.ic_record)

        binding.tvTimer.text = "00:00:00"
        amplitudes = binding.waveformView.clear()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onTimerTick(duration: String) {
        binding.tvTimer.text = duration
        this.duration = duration.dropLast(3)
        binding.waveformView.addAmplitude(recorder.maxAmplitude.toFloat())
    }
}