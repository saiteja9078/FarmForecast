package com.example.farmforecast.ui.diagnosis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.farmforecast.MainActivity
import com.example.farmforecast.databinding.FragmentLeafdiagnosisBinding
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import kotlin.math.min

class LeafDiagnosis : Fragment() {
    private var _binding: FragmentLeafdiagnosisBinding? = null
    private val binding get() = _binding!!

    private lateinit var tflite: Interpreter
    private lateinit var inputImageBuffer: TensorImage
    private lateinit var outputProbabilityBuffer: TensorBuffer

    private var currentProcessedBitmap: Bitmap? = null
    private var tempPhotoUri: Uri? = null

    // Translated strings cache
    private val translatedClassNames = mutableListOf<String>()
    private val translatedTreatments = mutableMapOf<String, String>()

    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempPhotoUri?.let { uri ->
                processAndDisplayImage(uri)
            }
        }
    }


    // Define separate launchers for different Android versions
    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            processAndDisplayImage(it)
        }
    }

    private val getContentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            processAndDisplayImage(it)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showPermissionDeniedDialog(getTranslatedText("Camera"))
        }
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            showPermissionDeniedDialog(getTranslatedText("Media"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeafdiagnosisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            tflite = Interpreter(loadModelFile(requireContext()))
            setupInputOutput()
        } catch (e: Exception) {
            showTranslatedToast("Error loading model: ${e.message}")
        }

        binding.imageCard.setOnClickListener {
            showImageSourceDialog()
        }

        binding.analyzeButton.setOnClickListener {
            currentProcessedBitmap?.let { bitmap ->
                analyzeImage(bitmap)
            } ?: run {
                showTranslatedToast("Please select an image first")
            }
        }

        translateUI()

        // Pre-translate disease names and treatments
        preTranslatePlantData()
        val mainActivity = activity as? MainActivity
        mainActivity?.translateAllViews(view)
    }

    private fun translateUI() {
        // Get list of view IDs you don't want to translate

        // Get reference to MainActivity
        val mainActivity = activity as? MainActivity

        // Translate upload text manually to ensure it updates
        mainActivity?.translateText("Tap to select or capture a leaf image") { translated ->
            binding.uploadText.text = translated
        }
    }

    private fun preTranslatePlantData() {
        val mainActivity = activity as? MainActivity

        // Translate class names
        mainActivity?.translateStrings(classNames) { translated ->
            translatedClassNames.clear()
            translatedClassNames.addAll(translated)
        }

        // Translate treatments (this might take time, so we do it at startup)
        mainActivity?.translateStrings(plantDiseaseTreatments.values.toList()) { translatedList ->
            translatedTreatments.clear()
            classNames.forEachIndexed { index, className ->
                val originalTreatment = plantDiseaseTreatments[className] ?: ""
                val translationIndex = plantDiseaseTreatments.values.toList().indexOf(originalTreatment)
                if (translationIndex >= 0 && translationIndex < translatedList.size) {
                    translatedTreatments[className] = translatedList[translationIndex]
                }
            }
        }
    }

    private fun getTranslatedText(text: String): String {
        // For immediate translation needs - return original text,
        // translation will happen asynchronously
        return text
    }

    private fun showTranslatedToast(message: String) {
        val mainActivity = activity as? MainActivity
        mainActivity?.translateText(message) { translated ->
            Toast.makeText(requireContext(), translated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        return FileUtil.loadMappedFile(context, "trained_plant_disease_model.tflite")
    }

    private fun setupInputOutput() {
        val inputTensor = tflite.getInputTensor(0)
        Log.d("ModelInfo", "Input Details: " +
                "Shape: ${inputTensor.shape().contentToString()}, " +
                "Type: ${inputTensor.dataType()}")

        val outputTensor = tflite.getOutputTensor(0)
        Log.d("ModelInfo", "Output Details: " +
                "Shape: ${outputTensor.shape().contentToString()}, " +
                "Type: ${outputTensor.dataType()}")

        val inputShape = tflite.getInputTensor(0).shape()
        val inputDataType = tflite.getInputTensor(0).dataType()
        val outputShape = tflite.getOutputTensor(0).shape()
        val outputDataType = tflite.getOutputTensor(0).dataType()

        inputImageBuffer = TensorImage(inputDataType)
        outputProbabilityBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType)
    }

    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(128, 128, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        return imageProcessor.process(tensorImage)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        try {
            val processedImage = preprocessImage(bitmap)
            tflite.run(processedImage.buffer, outputProbabilityBuffer.buffer.rewind())

            val probabilities = outputProbabilityBuffer.floatArray
            val maxProbabilityIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

            activity?.runOnUiThread {
                if (maxProbabilityIndex != -1 && maxProbabilityIndex < classNames.size) {
                    val diseaseName = classNames[maxProbabilityIndex]

                    // Use translated names if available
                    val translatedDiseaseName = if (translatedClassNames.isNotEmpty() &&
                        maxProbabilityIndex < translatedClassNames.size) {
                        translatedClassNames[maxProbabilityIndex]
                    } else {
                        diseaseName
                    }

                    // Use translated treatment if available
                    val treatment = translatedTreatments[diseaseName]
                        ?: plantDiseaseTreatments[diseaseName]
                        ?: "No diagnosis available"

                    // Format translated treatment text with line breaks
                    val formattedTreatment = treatment.replace(". ", ".\n\n")

                    binding.diseaseResult.text = translatedDiseaseName
                    binding.diagnosisResult.text = formattedTreatment

                } else {
                    // Translate default messages
                    val mainActivity = activity as? MainActivity
                    mainActivity?.translateText("Unknown Disease") { translated ->
                        binding.diseaseResult.text = translated
                    }

                    mainActivity?.translateText("Could not determine diagnosis. Please try a clearer image.") { translated ->
                        binding.diagnosisResult.text = translated
                    }
                }
            }
        } catch (e: Exception) {
            activity?.runOnUiThread {
                showTranslatedToast("Analysis failed: ${e.message}")
            }
        }
    }

    private fun createTempPhotoFile(): File? {
        return try {
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_",
                ".jpg",
                storageDir
            )
        } catch (e: IOException) {
            Log.e("Camera", "Error creating temp photo file: ${e.message}")
            null
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }

                Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.width, bitmap.height,
                    matrix, true
                )
            } ?: bitmap
        } catch (e: Exception) {
            Log.e("RotationError", "Failed to rotate image: ${e.message}")
            bitmap
        }
    }

    private fun cropAndResizeBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            size,
            size
        )
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }

    private fun processAndDisplayImage(uri: Uri) {
        try {
            // Step 1: Decode image dimensions only
            var inputStream = requireContext().contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Step 2: Calculate inSampleSize to scale down the image
            options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
            options.inJustDecodeBounds = false

            // Step 3: Load the scaled-down bitmap
            inputStream = requireContext().contentResolver.openInputStream(uri)
            var displayBitmap: Bitmap? = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Step 4: Rotate the bitmap if required
            displayBitmap = displayBitmap?.let { rotateBitmapIfRequired(it, uri) }

            // Step 5: Update the UI with the processed bitmap
            activity?.runOnUiThread {
                if (displayBitmap != null) {
                    binding.uploadIcon.visibility = View.VISIBLE
                    binding.uploadIcon.setImageBitmap(displayBitmap)

                    // Translate "Selected Image" text
                    val mainActivity = activity as? MainActivity
                    mainActivity?.translateText("Selected Image") { translated ->
                        binding.uploadText.text = translated
                    }
                } else {
                    showTranslatedToast("Failed to load image")
                }
            }

            // Step 6: Save the processed bitmap for analysis
            displayBitmap?.let { bitmap ->
                val processedBitmap = cropAndResizeBitmap(bitmap, 128)
                currentProcessedBitmap = processedBitmap
            }
        } catch (e: Exception) {
            activity?.runOnUiThread {
                showTranslatedToast("Error: ${e.message}")
            }
            Log.e("ImageProcessing", "Error: ${e.stackTraceToString()}")
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun showImageSourceDialog() {
        val mainActivity = activity as? MainActivity

        // Translate dialog options
        mainActivity?.translateStrings(listOf("Take Photo", "Choose from Gallery", "Select Image Source")) { translatedOptions ->
            val options = arrayOf(translatedOptions[0], translatedOptions[1])
            AlertDialog.Builder(requireContext())
                .setTitle(translatedOptions[2])
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> checkCameraPermission()
                        1 -> checkGalleryPermission()
                    }
                }
                .show()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog(getTranslatedText("Camera")) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - use the new photo picker or request READ_MEDIA_IMAGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ - prefer the photo picker
                openGallery()
            } else {
                // Android 13 - request permission if needed
                when {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        openGallery()
                    }
                    shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES) -> {
                        showPermissionRationaleDialog(getTranslatedText("Gallery Access")) {
                            mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        }
                    }
                    else -> {
                        mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    }
                }
            }
        } else {
            // Android 12 and below - request READ_EXTERNAL_STORAGE
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openGallery()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    showPermissionRationaleDialog(getTranslatedText("Storage Access")) {
                        mediaPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
                else -> {
                    mediaPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun openCamera() {
        val photoFile: File? = createTempPhotoFile()
        if (photoFile != null) {
            tempPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )
            cameraLauncher.launch(tempPhotoUri)
        } else {
            showTranslatedToast("Unable to create photo file")
        }
    }

    private fun openGallery() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                getContentLauncher.launch("image/*")
            }
        } catch (e: Exception) {
            showTranslatedToast("Failed to open gallery: ${e.message}")
            Log.e("GalleryError", "Failed to open gallery", e)
        }
    }

    private fun showPermissionRationaleDialog(title: String, onContinue: () -> Unit) {
        val mainActivity = activity as? MainActivity

        mainActivity?.translateStrings(
            listOf(
                title,
                "This permission is needed to select images for analysis. The app will only access the images you specifically choose.",
                "Continue",
                "Cancel"
            )
        ) { translatedTexts ->
            AlertDialog.Builder(requireContext())
                .setTitle(translatedTexts[0])
                .setMessage(translatedTexts[1])
                .setPositiveButton(translatedTexts[2]) { _, _ -> onContinue() }
                .setNegativeButton(translatedTexts[3], null)
                .show()
        }
    }

    private fun showPermissionDeniedDialog(permissionType: String) {
        val mainActivity = activity as? MainActivity

        mainActivity?.translateStrings(
            listOf(
                "Permission Denied",
                "You have denied $permissionType permission. Some features may not work.",
                "OK"
            )
        ) { translatedTexts ->
            AlertDialog.Builder(requireContext())
                .setTitle(translatedTexts[0])
                .setMessage(translatedTexts[1])
                .setPositiveButton(translatedTexts[2], null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        tflite.close()
    }

    // Class names and treatments
    private val classNames = listOf(
        "Apple: Apple scab",
        "Apple: Black rot",
        "Apple: Cedar apple rust",
        "Apple: Healthy",
        "Blueberry: Healthy",
        "Cherry (including sour): Powdery mildew",
        "Cherry (including sour): Healthy",
        "Corn (maize): Cercospora leaf spot, Gray leaf spot",
        "Corn (maize): Common rust",
        "Corn (maize): Northern Leaf Blight",
        "Corn (maize): Healthy",
        "Grape: Black rot",
        "Grape: Esca (Black Measles)",
        "Grape: Leaf blight (Isariopsis Leaf Spot)",
        "Grape: Healthy",
        "Orange: Huanglongbing (Citrus greening)",
        "Peach: Bacterial spot",
        "Peach: Healthy",
        "Pepper (bell): Bacterial spot",
        "Pepper (bell): Healthy",
        "Potato: Early blight",
        "Potato: Late blight",
        "Potato: Healthy",
        "Raspberry: Healthy",
        "Soybean: Healthy",
        "Squash: Powdery mildew",
        "Strawberry: Leaf scorch",
        "Strawberry: Healthy",
        "Tomato: Bacterial spot",
        "Tomato: Early blight",
        "Tomato: Late blight",
        "Tomato: Leaf Mold",
        "Tomato: Septoria leaf spot",
        "Tomato: Spider mites (Two-spotted spider mite)",
        "Tomato: Target Spot",
        "Tomato: Tomato Yellow Leaf Curl Virus",
        "Tomato: Tomato mosaic virus",
        "Tomato: Healthy"
    )

    private val plantDiseaseTreatments = mapOf(
        "Apple: Apple scab" to "Apply fungicides during the early season. Remove and destroy fallen leaves and infected fruit. Use resistant apple varieties.",
        "Apple: Black rot" to "Prune and destroy infected twigs, branches, and fruits. Apply fungicides preventatively. Maintain tree health with proper fertilization and watering.",
        "Apple: Cedar apple rust" to "Apply fungicides early in the season. Remove nearby cedar trees if possible, as they are alternate hosts.",
        "Apple: Healthy" to "No treatment needed. Maintain regular care to keep the plant healthy.",
        "Blueberry: Healthy" to "No treatment needed. Ensure proper soil conditions and adequate watering.",
        "Cherry (including sour): Powdery mildew" to "Apply sulfur-based or fungicidal sprays. Increase air circulation and avoid overhead watering.",
        "Cherry (including sour): Healthy" to "No treatment needed. Continue regular disease prevention practices.",
        "Corn (maize): Cercospora leaf spot, Gray leaf spot" to "Use resistant varieties. Apply fungicides if necessary and practice crop rotation.",
        "Corn (maize): Common rust" to "Plant resistant varieties and apply fungicides if needed. Maintain good field hygiene.",
        "Corn (maize): Northern Leaf Blight" to "Use resistant seeds and rotate crops. Apply fungicides if required.",
        "Corn (maize): Healthy" to "No treatment needed. Maintain regular care.",
        "Grape: Black rot" to "Prune and remove infected vines. Apply fungicides in early spring. Ensure good air circulation.",
        "Grape: Esca (Black Measles)" to "Remove infected wood. Ensure vines are not stressed and avoid trunk or root injuries.",
        "Grape: Leaf blight (Isariopsis Leaf Spot)" to "Apply fungicides and prune affected leaves. Increase ventilation around vines.",
        "Grape: Healthy" to "No treatment needed. Maintain proper care.",
        "Orange: Huanglongbing (Citrus greening)" to "No known cure. Remove and destroy infected trees. Control psyllid vectors and plant disease-free stock.",
        "Peach: Bacterial spot" to "Apply copper-based bactericides. Use resistant varieties if available. Prune and remove affected parts.",
        "Peach: Healthy" to "No treatment needed. Ensure good tree health.",
        "Pepper (bell): Bacterial spot" to "Apply copper-based sprays. Practice crop rotation and use certified disease-free seeds.",
        "Pepper (bell): Healthy" to "No treatment needed. Maintain regular care.",
        "Potato: Early blight" to "Apply fungicides and remove infected plant debris. Practice crop rotation.",
        "Potato: Late blight" to "Use resistant varieties. Apply fungicides and destroy infected plants.",
        "Potato: Healthy" to "No treatment needed. Continue regular monitoring.",
        "Raspberry: Healthy" to "No treatment needed. Maintain proper care and disease prevention practices.",
        "Soybean: Healthy" to "No treatment needed. Practice regular crop management.",
        "Squash: Powdery mildew" to "Apply fungicides. Improve air circulation and avoid overhead watering.",
        "Strawberry: Leaf scorch" to "Remove and destroy infected leaves. Apply appropriate fungicides.",
        "Strawberry: Healthy" to "No treatment needed. Maintain regular care.",
        "Tomato: Bacterial spot" to "Use copper sprays and disease-resistant seeds. Practice crop rotation.",
        "Tomato: Early blight" to "Apply fungicides and remove affected plant parts.",
        "Tomato: Late blight" to "Apply fungicides and remove and destroy infected plants. Use resistant varieties.",
        "Tomato: Leaf Mold" to "Increase air circulation and apply fungicides. Prune lower leaves.",
        "Tomato: Septoria leaf spot" to "Apply fungicides and remove infected leaves. Ensure good ventilation.",
        "Tomato: Spider mites (Two-spotted spider mite)" to "Use insecticidal soap or horticultural oil. Increase humidity around plants.",
        "Tomato: Target Spot" to "Apply fungicides and remove infected plant debris.",
        "Tomato: Tomato Yellow Leaf Curl Virus" to "Control whitefly populations. Use resistant varieties and remove infected plants.",
        "Tomato: Tomato mosaic virus" to "Remove and destroy infected plants. Sanitize tools and avoid tobacco products.",
        "Tomato: Healthy" to "No treatment needed. Maintain regular plant care."
    )
}