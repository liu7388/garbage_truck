package com.example.garbage_truck

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.garbage_truck.databinding.FragmentClassificationBinding
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.MappedByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ClassificationFragment : Fragment() {

    private var _binding: FragmentClassificationBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var interpreter: Interpreter? = null
    private lateinit var labels: List<String>
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClassificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupInterpreter()
        cameraPermissionRequest.launch(Manifest.permission.CAMERA)

        binding.cameraCaptureButton.setOnClickListener { takePhoto() }
    }

    private fun setupInterpreter() {
        try {
            val model = loadModelFile(requireContext(), "garbage.tflite")
            interpreter = Interpreter(model)
            labels = FileUtil.loadLabels(requireContext(), "labels.txt")

            // Get model input shape
            val inputTensor = interpreter!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            inputImageHeight = inputShape[1]
            inputImageWidth = inputShape[2]

        } catch (e: IOException) {
            Log.e("ClassificationFragment", "Error setting up interpreter", e)
            Toast.makeText(requireContext(), "Failed to load model.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("ClassificationFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    classifyImage(bitmap)
                    image.close()
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("ClassificationFragment", "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    private fun classifyImage(bitmap: Bitmap) {
        interpreter ?: return

        // 1. Create TensorImage from the Bitmap
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        // 2. Create an ImageProcessor to match model requirements
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-imageCapture!!.targetRotation / 90)) // Correct rotation
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR)) // Resize
            .add(NormalizeOp(0.0f, 255.0f)) // Normalize to [0,1]
            .build()

        val processedImage = imageProcessor.process(tensorImage)

        // 3. Define the output buffer
        val outputBuffer = TensorBuffer.createFixedSize(interpreter!!.getOutputTensor(0).shape(), DataType.FLOAT32)

        // 4. Run inference
        interpreter?.run(processedImage.buffer, outputBuffer.buffer.rewind())

        // 5. Process the result
        val scores = outputBuffer.floatArray
        var maxScore = 0f
        var maxScoreIndex = -1
        scores.forEachIndexed { index, score ->
            if (score > maxScore) {
                maxScore = score
                maxScoreIndex = index
            }
        }

        if (maxScoreIndex != -1) {
            val resultLabel = labels[maxScoreIndex]
            val resultText = "$resultLabel (${String.format("%.2f%%", maxScore * 100)})"
            val garbageType = when (resultLabel) {
                "cardboard", "paper" -> "紙類"
                "metal" -> "鐵鋁罐類"
                "plastic" -> "塑膠類"
                "glass" -> "玻璃類"
                else -> "一般垃圾"
            }
            val isRecyclable = garbageType != "一般垃圾"

            activity?.runOnUiThread {
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_classification_result, null)
                val iconImageView = dialogView.findViewById<ImageView>(R.id.iv_garbage_type_icon)
                val resultTextView = dialogView.findViewById<TextView>(R.id.tv_classification_result)
                val typeTextView = dialogView.findViewById<TextView>(R.id.tv_garbage_type)

                if (isRecyclable) {
                    iconImageView.setImageResource(R.drawable.ic_recycling)
                    iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), when (resultLabel) {
                        "paper", "cardboard" -> R.color.recycled_paper
                        "metal" -> R.color.recycled_metal
                        "plastic" -> R.color.recycled_plastic
                        "glass" -> R.color.recycled_glass
                        else -> R.color.black
                    }), PorterDuff.Mode.SRC_IN)
                } else {
                    iconImageView.setImageResource(R.drawable.ic_trash)
                }

                resultTextView.text = resultText
                typeTextView.text = garbageType

                AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .setPositiveButton("確定", null)
                    .show()
            }
        } else {
            activity?.runOnUiThread {
                AlertDialog.Builder(requireContext())
                    .setTitle("辨識結果")
                    .setMessage("無法辨識")
                    .setPositiveButton("確定", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        interpreter?.close()
        cameraExecutor.shutdown()
        _binding = null
    }
}
