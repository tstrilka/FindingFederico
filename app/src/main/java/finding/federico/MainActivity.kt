package finding.federico

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import finding.federico.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias ColorListener = (color: String) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ColorAnalyzer { color ->
                        Log.d(TAG, "Dominant color: $color")
                        if (isGreen(color)) {
                            runOnUiThread {
                                viewBinding.message.apply {
                                    text = getString(R.string.federico_text)
                                }
                            }
                        } else {
                            runOnUiThread {
                                viewBinding.message.apply {
                                    text = getString(R.string.not_federico_text)
                                }
                            }
                        }
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun isGreen(color: String): Boolean {
        val r = color.substring(1..2).toInt(16)
        val g = color.substring(3..4).toInt(16)
        val b = color.substring(5..6).toInt(16)
        return g > r && g > b
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "FindingFederico"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private class ColorAnalyzer(private val listener: ColorListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        private fun getRGBfromYUV(image: ImageProxy): Triple<Double, Double, Double> {
            val planes = image.planes

            val height = image.height
            val width = image.width

            // Y
            val yArr = planes[0].buffer
            val yArrByteArray = yArr.toByteArray()
            val yPixelStride = planes[0].pixelStride
            val yRowStride = planes[0].rowStride

            // U
            val uArr = planes[1].buffer
            val uArrByteArray =uArr.toByteArray()
            val uPixelStride = planes[1].pixelStride
            val uRowStride = planes[1].rowStride

            // V
            val vArr = planes[2].buffer
            val vArrByteArray = vArr.toByteArray()
            val vPixelStride = planes[2].pixelStride
            val vRowStride = planes[2].rowStride

            val y = yArrByteArray[(height * yRowStride + width * yPixelStride) / 2].toInt() and 255
            val u = (uArrByteArray[(height * uRowStride + width * uPixelStride) / 4].toInt() and 255) - 128
            val v = (vArrByteArray[(height * vRowStride + width * vPixelStride) / 4].toInt() and 255) - 128

            val r = y + (1.370705 * v)
            val g = y - (0.698001 * v) - (0.337633 * u)
            val b = y + (1.732446 * u)

            return Triple(r,g,b)
        }

        // analyze the color
        override fun analyze(image: ImageProxy) {
                val colors = getRGBfromYUV(image)
                val hexColor = String.format("#%02x%02x%02x", colors.first.toInt(), colors.second.toInt(), colors.third.toInt())
                Log.d("test", "hexColor: $hexColor")
                listener(hexColor)
                image.close()
        }
    }
}