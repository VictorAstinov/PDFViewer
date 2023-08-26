package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PersistableBundle
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.

var PDF_WIDTH = 0
var PDF_HEIGHT = 0
var LANDSCAPE_SCALE = 1.0F
var PORTRAIT_SCALE = 1.0F
class MainActivity : AppCompatActivity() {
    private val LOGNAME = "pdf_viewer"
    private val FILENAME = "shannon1948.pdf"
    private val FILERESID = R.raw.shannon1948

    private val KEY = "BUNDLEKEY"
    private val PAGEKEY = "PAGEKEY"
    private val MODEKEY = "MODE"

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null

    // custom ImageView class that captures strokes and draws them over the image

    // current page number
    private var pageNum = 0

    private var pages = ArrayList<PDFimage>()

    private var mode = -1

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (PDF_HEIGHT == 0 && PDF_WIDTH == 0) {
            PDF_WIDTH = (this.windowManager.currentWindowMetrics.bounds.width() * 1.0).toInt()
            PDF_HEIGHT = (this.windowManager.currentWindowMetrics.bounds.height() * 1.0).toInt()
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                LANDSCAPE_SCALE = (PDF_HEIGHT.toFloat() / PDF_WIDTH)
            }
            else {
                PORTRAIT_SCALE= (PDF_HEIGHT.toFloat() / PDF_WIDTH)
            }
            // LANDSCAPE_SCALE = (PDF_HEIGHT.toFloat() / PDF_WIDTH)
        }


        val layout = findViewById<LinearLayout>(R.id.pdfLayout).findViewById<LinearLayout>(R.id.pdfWindow)
        layout.isEnabled = true

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY) && savedInstanceState.containsKey(PAGEKEY)) {
            layout.removeAllViews()
            pages = savedInstanceState[KEY] as ArrayList<PDFimage>
            pageNum = savedInstanceState.getInt(PAGEKEY)
            mode = savedInstanceState.getInt(MODEKEY)
        }
        else {
            pages.add(PDFimage(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                minimumWidth = 2000
                // minimumHeight = 3000
            })
            // mode = -1
        }

        // Log.d("TEST:V", "Parent: ${pages[pageNum].parent}")
        layout.addView(pages[pageNum])
        // pages[pageNum].setMode(mode)


        val listener : RadioGroup.OnCheckedChangeListener =
            RadioGroup.OnCheckedChangeListener { g, p1 ->
                for (i in 0 until g.childCount) {
                    val button = g[i] as ToggleButton
                    button.isChecked = (p1 == button.id)
                }
            }
        findViewById<RadioGroup>(R.id.BrushGroup).setOnCheckedChangeListener(listener)


        // set title
        // TODO(set fonts for title, status)
        findViewById<TextView>(R.id.Title).apply {
            text = FILENAME
        }

        val nextButton = findViewById<ImageButton>(R.id.ForwardButton)
        nextButton.setOnClickListener {
            findViewById<RadioGroup>(R.id.BrushGroup).clearCheck()
            pageNum = min(pageNum + 1, pdfRenderer.pageCount - 1)
            pages.add(PDFimage(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            pages[pageNum].minimumWidth = 2000
            layout.removeViewAt(0)
            layout.addView(pages[pageNum])

            try { // set init page number
                findViewById<TextView>(R.id.PageNumbers).apply{
                    text = getString(R.string.page) + ": ${pageNum + 1} / ${pdfRenderer.pageCount}"
                }
                showPage(pageNum)
            } catch (exception: IOException) {
                Log.d(LOGNAME, "Error opening PDF")
            }
        }

        val backButton = findViewById<ImageButton>(R.id.BackButton)
        backButton.setOnClickListener {
            pageNum = max(pageNum - 1, 0)
            findViewById<RadioGroup>(R.id.BrushGroup).clearCheck()
            layout.removeViewAt(0)
            layout.addView(pages[pageNum])

            try { // set init page number
                findViewById<TextView>(R.id.PageNumbers).apply{
                    text = getString(R.string.page) + ": ${pageNum + 1} / ${pdfRenderer.pageCount}"
                }
                showPage(pageNum)
            } catch (exception: IOException) {
                Log.d(LOGNAME, "Error opening PDF")
            }
        }

        val undoButton = findViewById<ImageButton>(R.id.UndoButton)
        undoButton.setOnClickListener {
            pages[pageNum].undo()
        }

        val redoButton = findViewById<ImageButton>(R.id.RedoButton)
        redoButton.setOnClickListener {
            pages[pageNum].redo()
        }

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            // set init page number
            findViewById<TextView>(R.id.PageNumbers).apply{
                text = getString(R.string.page) + ": ${pageNum + 1} / ${pdfRenderer.pageCount}"
            }
            showPage(pageNum)
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }

    fun toggleDraw(v : View) {
        val par = ((v as ToggleButton).parent as RadioGroup)
        par.check(v.id)

        // set mode across all pages to keep up to date with toolbar
        for (p in pages) {
            // mode = p.setMode(v.id)
            p.setMode(v.id)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(KEY, pages)
        outState.putSerializable(PAGEKEY, pageNum)
        outState.putSerializable(MODEKEY, mode)
        // Log.d("TEST:V", "SAVING")
    }




    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState.containsKey(KEY) && savedInstanceState.containsKey(PAGEKEY)) {
            Log.d("TEST:V", "CONTAINS KEYS, LOADING DRAWINGS")
            pages = savedInstanceState[KEY] as ArrayList<PDFimage>
            pageNum = savedInstanceState.getInt(PAGEKEY)
            mode = savedInstanceState.getInt(MODEKEY)
        }
        if (mode < 0) {
            findViewById<RadioGroup>(R.id.BrushGroup).clearCheck()
        }
        // pages[pageNum].setMode(mode)
    }




    // just to print out window heights
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // val layout = findViewById<LinearLayout>(R.id.pdfImage)
        val pdfLayout = findViewById<LinearLayout>(R.id.pdfLayout)
        val w = pdfLayout.findViewById<LinearLayout>(R.id.pdfWindow)
        // Log.d("TEST:V", "page : ${pageImage.height}")
        // Log.d("TEST:V","layout : ${layout.height}")
        Log.d("TEST:V", "${window.windowManager.currentWindowMetrics.bounds}")
        Log.d("TEST:V","pdf layout : ${pdfLayout.width}, ${pdfLayout.height}")
        Log.d("TEST:V","pdf window : ${w.width}, ${w.height}")
        Log.d("TEST:V", "bitmap size: ${PDFbitmap?.width}, ${PDFbitmap?.height}")
        Log.d("TEST:V", "bitmap scale: ${(pdfLayout.width / (PDFbitmap?.width ?: 1)).toFloat()}, ${(pdfLayout.height / (PDFbitmap?.height ?: 1)).toFloat()}")
        // currentMatrix.setScale((pdfLayout.height.toFloat() / (PDFbitmap?.height ?: 1)), (pdfLayout.width.toFloat() / (PDFbitmap?.width ?: 1)))
        // PDFbitmap = PDFbitmap?.let { Bitmap.createScaledBitmap(it, w.width, w.height, false) }
        // PDF_WIDTH = (this.windowManager.currentWindowMetrics.bounds.width() * 0.8).toInt()
        // PDF_HEIGHT = (this.windowManager.currentWindowMetrics.bounds.height() * 0.8).toInt()
        Log.d("TEST:V", "ORIENTATION")
        // showPage(pageNum)
    }


    override fun onDestroy() {
        super.onDestroy()
        findViewById<LinearLayout>(R.id.pdfLayout).findViewById<LinearLayout>(R.id.pdfWindow).removeAllViews()
        try {
            closeRenderer()
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).

            val bitmap = Bitmap.createBitmap(PDF_WIDTH, PDF_HEIGHT, Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pages[pageNum].setImage(bitmap)
            pages[pageNum].setMode(mode)
            if (mode > 0) {
                toggleDraw(findViewById(mode))
            }
        }
    }
}