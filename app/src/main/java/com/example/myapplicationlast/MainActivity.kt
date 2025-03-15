package com.example.myapplicationlast
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.myapplicationlast.databinding.ActivityMainBinding
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
import android.Manifest
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.view.View
import androidx.appcompat.app.AlertDialog
import kotlin.math.min

private val REQUEST_CODE_CAMERA = 1001
private val REQUEST_CODE_GALLERY = 1002


class MainActivity : AppCompatActivity() {
    private val PICK_IMAGE = 1
    private val CAMERA_REQUEST = 2
    private var currentImageUri: Uri? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var tflite: Interpreter
    private lateinit var inputImageBuffer: TensorImage
    private lateinit var outputProbabilityBuffer: TensorBuffer

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            tflite = Interpreter(loadModelFile(this))
            setupInputOutput()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading model: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // In onCreate(), replace the existing imageCard click listener with:
        binding.imageCard.setOnClickListener {
            showImageSourceDialog()
        }

        binding.analyzeButton.setOnClickListener {
            currentProcessedBitmap?.let { bitmap ->
                analyzeImage(bitmap)
            } ?: run {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        return FileUtil.loadMappedFile(context, "trained_plant_disease_model.tflite")
    }

    private fun setupInputOutput()
    {
        val inputTensor = tflite.getInputTensor(0)
        Log.d("ModelInfo", "Input Details: " +
                "Shape: ${inputTensor.shape().contentToString()}, " +
                "Type: ${inputTensor.dataType()}")

        val outputTensor = tflite.getOutputTensor(0)
        Log.d("ModelInfo", "Output Details: " +
                "Shape: ${outputTensor.shape().contentToString()}, " +
                "Type: ${outputTensor.dataType()}")
        // Input shape (adjust based on your model)
        val inputShape = tflite.getInputTensor(0).shape()
        val inputDataType = tflite.getInputTensor(0).dataType()

        // Output shape (adjust based on your model)
        val outputShape = tflite.getOutputTensor(0).shape()
        val outputDataType = tflite.getOutputTensor(0).dataType()

        inputImageBuffer = TensorImage(inputDataType)
        outputProbabilityBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType)
    }

    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        // Match Python's preprocessing (no normalization)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(128, 128, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        // Load bitmap into TensorImage with FLOAT32 type (no scaling)
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        return imageProcessor.process(tensorImage)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        try {
            // 1. Get bitmap from URI
            val processedImage = preprocessImage(bitmap)



            // 3. Run inference
            tflite.run(processedImage.buffer, outputProbabilityBuffer.buffer.rewind())

            // 4. Get prediction results
            val probabilities = outputProbabilityBuffer.floatArray
            val maxProbabilityIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

            // 5. Update UI
            runOnUiThread {
                if (maxProbabilityIndex != -1 && maxProbabilityIndex < classNames.size) {
                    val diseaseName = classNames[maxProbabilityIndex]
                    val diagnosis = plantDiseaseTreatments[diseaseName] ?: "No diagnosis available"

                    // Update disease name
                    binding.diseaseResult.text = diseaseName

                    // Update diagnosis with proper formatting
                    binding.diagnosisResult.text = diagnosis.replace(". ", ".\n\n")
                } else {
                    binding.diseaseResult.text = "Unknown Disease"
                    binding.diagnosisResult.text = "Could not determine diagnosis. Please try a clearer image."
                }
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var tempPhotoUri: Uri? = null
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile: File? = createTempPhotoFile()
            if (photoFile != null) {
                tempPhotoUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    photoFile
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
                startActivityForResult(intent, CAMERA_REQUEST)
            } else {
                Toast.makeText(this, "Unable to create photo file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }
    private fun createTempPhotoFile(): File? {
        return try {
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_", // Prefix
                ".jpg", // Suffix
                storageDir // Directory
            )
        } catch (e: IOException) {
            Log.e("Camera", "Error creating temp photo file: ${e.message}")
            null
        }
    }
    private var currentProcessedBitmap: Bitmap? = null

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return bitmap
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
        } catch (e: Exception) {
            Log.e("RotationError", "Failed to rotate image: ${e.message}")
            bitmap
        }
    }
    private fun cropAndResizeBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        // First crop to square
        val size = min(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            size,
            size
        )

        // Then resize to target size
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE -> {
                    data?.data?.let { uri ->
                        processAndDisplayImage(uri)
                    }
                }
                CAMERA_REQUEST -> {
                    tempPhotoUri?.let { uri ->
                        processAndDisplayImage(uri)
                    }
                }
            }
        }
    }
    private fun processAndDisplayImage(uri: Uri) {
        try {
            // Step 1: Decode image dimensions only
            var inputStream = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Step 2: Calculate inSampleSize to scale down the image
            options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
            options.inJustDecodeBounds = false

            // Step 3: Load the scaled-down bitmap
            inputStream = contentResolver.openInputStream(uri)
            var displayBitmap: Bitmap? = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Step 4: Rotate the bitmap if required
            displayBitmap = displayBitmap?.let { rotateBitmapIfRequired(it, uri) }

            // Step 5: Update the UI with the processed bitmap
            runOnUiThread {
                if (displayBitmap != null) {
                    binding.uploadIcon.visibility = View.VISIBLE
                    binding.uploadIcon.setImageBitmap(displayBitmap)
                    binding.uploadText.text = "Selected Image"
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }

            // Step 6: Save the processed bitmap for analysis
            displayBitmap?.let { bitmap ->
                val processedBitmap = cropAndResizeBitmap(bitmap, 128) // Match model input size
                currentProcessedBitmap = processedBitmap
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(
                        this,
                        "Camera permission required for taking photos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            REQUEST_CODE_GALLERY -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(
                        this,
                        "Storage permission required for gallery access",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA
            )
        } else {
            openCamera()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> checkGalleryPermission()
                }
            }
            .show()
    }
    private fun checkGalleryPermission() {
        // For Android 13+ (API 33), no permission needed for gallery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openGallery()
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_GALLERY
                )
            } else {
                openGallery()
            }
        }
    }


    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, PICK_IMAGE)
        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
        }
    }
    private fun openCameraOrGallery() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select an option")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }
    override fun onDestroy() {
        super.onDestroy()
        tflite.close()
    }
}