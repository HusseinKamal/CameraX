package com.hussein.camerax

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.MatrixExt.postRotate
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hussein.camerax.ui.theme.CameraXTheme
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var recording : Recording? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!hasRequiredPermissions()){
            ActivityCompat.requestPermissions(this, CAMERAX_PERMISSIONS , 0)
        }
        setContent {
            CameraXTheme {
                val scope = rememberCoroutineScope()
                val scafollState = rememberBottomSheetScaffoldState()

                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(
                            CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE
                        )
                    }
                }

                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()
                BottomSheetScaffold(sheetContent = {
                    PhotoBottomSheetContent(
                        bitmaps = bitmaps,
                        modifier = Modifier.fillMaxWidth()
                    )
                }, sheetPeekHeight = 0.dp, scaffoldState = scafollState) {paddingValues ->  
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)){
                        CameraPreview(
                            controller = controller,
                            modifier =  Modifier.fillMaxSize()
                        )
                        
                        IconButton(onClick = {
                            controller.cameraSelector =
                                if(controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA){
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else CameraSelector.DEFAULT_BACK_CAMERA
                        },
                            modifier = Modifier.offset(16.dp,16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cameraswitch , contentDescription = "Switch Camera")
                        }
                    }

                    Row(modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround) {
                        IconButton(onClick = {
                            scope.launch {
                                scafollState.bottomSheetState.expand() //Open Bottom sheet
                            }
                        },
                            modifier = Modifier.offset(16.dp,16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.BrowseGallery , contentDescription = "Open Gallery")
                        }

                        IconButton(onClick = {
                                             takePhoto(controller = controller,
                                             onPhotoTake = viewModel::onTakePhoto)
                        },
                            modifier = Modifier.offset(16.dp,16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Photo , contentDescription = "Take Photo")
                        }

                        /** Record Video */
                        IconButton(onClick = {
                            recordVideo(controller)
                        },
                            modifier = Modifier.offset(16.dp,16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Videocam , contentDescription = "Record Video")
                        }
                    }
                }
            }
        }
    }
    
    private fun hasRequiredPermissions():Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTake : (Bitmap) -> Unit
    ){
        if(!hasRequiredPermissions()){
            return
        }
        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object :OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                        postScale(-1f,1f)
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )
                    onPhotoTake(rotatedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("Camera" , "Couldn't take photo: ," ,exception)
                }

            }
        )
    }

    private fun recordVideo(controller: LifecycleCameraController){
        if(recording != null){
            recording?.stop()
            recording = null
            return
        }
        if(!hasRequiredPermissions()){
            return
        }
        val outputFile = File(filesDir,"my-recording.mp4")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        recording = controller.startRecording(
            FileOutputOptions.Builder(outputFile).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(applicationContext)
        ){event->
            when(event){
                is VideoRecordEvent.Finalize ->{
                    if(event.hasError()){
                        recording?.close()
                        recording = null
                        Toast.makeText(applicationContext,"Video capture failed",Toast.LENGTH_SHORT).show()
                    }
                    else {
                        Toast.makeText(applicationContext,"Video capture succeeded",Toast.LENGTH_SHORT).show()

                    }
                }
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CameraXTheme {
    }
}