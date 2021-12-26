package com.example.drawing

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.contextaware.withContextAvailable
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.custom_brush_size.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView?= null
    private var mImageCurrentButtonPaint: ImageButton?= null
    private var customDialog: Dialog? = null
    private val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
           if (result.resultCode == RESULT_OK && result.data != null) {
               val imageBackground: ImageView = findViewById(R.id.iv_background)
               imageBackground.setImageURI(result!!.data?.data)
           }
    }
    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted) {
                    Toast.makeText(
                        this@MainActivity, "Permission granted now you can read" +
                                "the storage file", Toast.LENGTH_SHORT
                    ).show()
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            this@MainActivity, "Permission is not granted now you can read" +
                                    " the storage file", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawingView)
        drawingView!!.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColor = findViewById<LinearLayout>(R.id.ll_colors)
        mImageCurrentButtonPaint = linearLayoutPaintColor[0] as ImageButton
        mImageCurrentButtonPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )
        ib_brush.setOnClickListener {
            showBrushSizeDialog()
        }
        ib_undo.setOnClickListener {
            drawingView!!.undo()
        }
        ib_gallery.setOnClickListener {
            requestStoragePermission()
        }
        ib_save.setOnClickListener {
            if(isReadStorageAllowed()) {
                showProgressBarDialog()
                lifecycleScope.launch {
                    val flDrawingView:FrameLayout = findViewById(R.id.fl_drawing_view)
                    saveBitmapFile(gettingMapFromView(flDrawingView))
                }
            }
        }
    }
    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.custom_brush_size) // required
        brushDialog.setTitle("Brush size: ")
        val smallBtn = brushDialog.ib_brush_small
        smallBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.ib_brush_medium
        mediumBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.ib_brush_large
        largeBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show() // required
    }
    fun paintClicked(view: View) {
        if (view != mImageCurrentButtonPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )
            mImageCurrentButtonPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_nomal)
            )
            mImageCurrentButtonPaint = view
        }
    }
    private fun showRationaleDialog(title: String, message: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this) // required
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") {dialog, _ -> // required
                dialog.dismiss()
            }
        builder.create().show() // required
    }
    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale( // check if user permit
                this, Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Drawing App ", "Drawing App needs to access for storage.")
        }
        else {
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }
    private fun gettingMapFromView(view: View): Bitmap? {
        // create a Bitmap
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap) // create canvas hold bitmap
        val bgDrawable = view.background // take background by view
        if (bgDrawable != null) {
            bgDrawable.draw(canvas) // if has bg draw it on canvas
        }
        else {
            canvas.drawColor(Color.WHITE) // if no draw color white on canvas
        }
        view.draw(canvas)
        return returnedBitmap
    }
    private suspend fun saveBitmapFile(mBitmap: Bitmap?) : String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val f = File(externalCacheDir?.absoluteFile.toString() + File.separator
                        + "DrawingApp_" + System.currentTimeMillis()/ 1000  + ".jpg")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    runOnUiThread {
                        cancelProgressBarDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "File save successfully: $result",
                            Toast.LENGTH_LONG).show()
                        }
                        else {
                            Toast.makeText(this@MainActivity, "Something wrong while saving file",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
                catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
    private fun isReadStorageAllowed() : Boolean {
        val result = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun showProgressBarDialog() {
        customDialog = Dialog(this@MainActivity)
        customDialog?.setContentView(R.layout.custom_progressbar_dialog)
        customDialog?.show()
    }
    private fun cancelProgressBarDialog() {
        customDialog?.dismiss()
        customDialog = null
    }
}