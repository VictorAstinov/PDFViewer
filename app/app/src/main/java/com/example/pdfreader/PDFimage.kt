package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import java.io.Serializable

var PDFbitmap : Bitmap? = null

private val paint = Paint().apply {
    strokeWidth = 5F
    isAntiAlias = true
    style = Paint.Style.STROKE
    color = Color.RED
}

private val highlightColor = Paint().apply {
    strokeWidth = 20F
    isAntiAlias = true
    style = Paint.Style.STROKE
    color = Color.YELLOW
    alpha = 90
}

private val brushes = listOf(paint, highlightColor)

var currentMatrix = Matrix()
var inverse = Matrix()
private val defaultMatrix = Matrix()

private var path: SerializablePath = SerializablePath()

// The panning and zooming comes from the Android PanZoom demo code here:
// https://git.uwaterloo.ca/cs349/public/sample-code/-/tree/master/15.Android/PanZoom
@SuppressLint("AppCompatCustomView")
class PDFimage (context: Context?) : ImageView(context), Serializable {
    val LOGNAME = "pdf_image"

    // drawing path
    // var path: Path = Path()
    private var paths = ArrayList<Pair<SerializablePath, Int>>() // this is the state for undo/redo stack, should be saved

    private var mode = -1
    private val ADDED = 1
    private val DELETED = 0

    private val PAINT = 0
    private val HIGHLIGHT = 1

    private val undoAction = ArrayList<Triple<SerializablePath, Int, Int>>()
    private val redoAction = ArrayList<Triple<SerializablePath, Int, Int>>()

    init {
        // this.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    private var scale = 1.0F

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    // var currentMatrix = Matrix()
    // var inverse = Matrix()

    // capture touch events (down/move/up) to create a path/stroke that we draw later
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var inverted = floatArrayOf()
        when (event.pointerCount) {
            1 -> {
                if (mode < 0) {
                    return true
                }
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // invert using the current matrix to account for pan/scale
                // inverts in-place and returns boolean
                inverse = Matrix()
                currentMatrix.invert(inverse)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)
                x1 = inverted[0]
                y1 = inverted[1]
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(LOGNAME, "Action down")
                        path = SerializablePath()
                        if (mode == R.id.HighlightButton) {
                            paths.add(Pair(path, HIGHLIGHT))
                        }
                        else if (mode == R.id.DrawButton) {
                            paths.add(Pair(path, PAINT))
                        }
                        path!!.moveTo(x1, y1)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        Log.d(LOGNAME, "Action move")
                        path!!.lineTo(x1, y1)
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d(LOGNAME, "Action up")
                        if (mode == R.id.EraseButton) {
                            val iter = paths.iterator()
                            while (iter.hasNext()) {
                                val p = iter.next()
                                val result = Path()
                                path.let {
                                    result.op(it, p.first, Path.Op.INTERSECT)
                                }
                                if (!result.isEmpty) {
                                    undoAction.add(Triple(p.first, p.second, DELETED))
                                    iter.remove()
                                    break // just remove 1 line
                                }
                            }
                        }
                        else if (mode == R.id.DrawButton) {
                            undoAction.add(Triple(path, PAINT, ADDED))
                        }
                        else {
                            undoAction.add(Triple(path, HIGHLIGHT, ADDED))
                        }
                        // clear redo stack
                        redoAction.clear()
                    }
                }
            }
            2 -> {
                // point 1
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    x1 = inverted.get(0)
                    old_x1 = x1
                    y1 = inverted.get(1)
                    old_y1 = y1
                } else {
                    old_x1 = x1
                    old_y1 = y1
                    x1 = inverted.get(0)
                    y1 = inverted.get(1)
                }

                // point 2
                p2_id = event.getPointerId(1)
                p2_index = event.findPointerIndex(p2_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    x2 = inverted.get(0)
                    old_x2 = x2
                    y2 = inverted.get(1)
                    old_y2 = y2
                } else {
                    old_x2 = x2
                    old_y2 = y2
                    x2 = inverted.get(0)
                    y2 = inverted.get(1)
                }

                // midpoint
                mid_x = (x1 + x2) / 2
                mid_y = (y1 + y2) / 2
                old_mid_x = (old_x1 + old_x2) / 2
                old_mid_y = (old_y1 + old_y2) / 2

                // distance
                val d_old =
                    Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                        .toFloat()
                val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                    .toFloat()

                // pan and zoom during MOVE event
                if (event.action == MotionEvent.ACTION_MOVE) {
                    Log.d(LOGNAME, "Multitouch move")
                    // pan == translate of midpoint
                    val dx = mid_x - old_mid_x
                    val dy = mid_y - old_mid_y
                    currentMatrix.preTranslate(dx, dy)
                    Log.d(LOGNAME, "translate: $dx,$dy")

                    // zoom == change of spread between p1 and p2
                    var scale = d / d_old
                    scale = Math.max(0f, scale)
                    currentMatrix.preScale(scale, scale, mid_x, mid_y)
                    Log.d(LOGNAME, "scale: $scale")

                    // reset on up
                } else if (event.action == MotionEvent.ACTION_UP) {
                    old_x1 = -1f
                    old_y1 = -1f
                    old_x2 = -1f
                    old_y2 = -1f
                    old_mid_x = -1f
                    old_mid_y = -1f
                }
            }
            else -> {
            }
        }
        invalidate()
        return true
    }


    fun undo() {
        if (undoAction.isEmpty()) {
            return
        }
        val last = undoAction.last()
        redoAction.add(last)
        undoAction.removeLast()

        if (last.third == ADDED) {
            val iter = paths.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                if (p.first == last.first) {
                    Log.d("TEST:V", "SUCCESSFUL REMOVAL")
                    iter.remove()
                    break
                }
            }
        }
        else {
            paths.add(Pair(last.first, last.second))
        }
        invalidate()
    }

    fun redo() {
        if (redoAction.isEmpty()) {
            return
        }
        val last = redoAction.last()
        undoAction.add(last)
        redoAction.removeLast()

        if (last.third == ADDED) {
            paths.add(Pair(last.first, last.second))
        }
        else {
            val iter = paths.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                if (p.first == last.first) {
                    Log.d("TEST:V", "SUCCESSFUL REMOVAL")
                    iter.remove()
                    break
                }
            }
        }
        invalidate()
    }


    // set image as background
    fun setImage(b: Bitmap) {
        PDFbitmap = b
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            scale = PORTRAIT_SCALE
            currentMatrix.setScale(scale, 1 / scale)
        }
        else {
            scale = LANDSCAPE_SCALE
            currentMatrix.setScale(scale, 1/ scale)
        }
        // currentMatrix.setScale(scale, 1 / scale)
    }


    fun setMode(m : Int) : Int {
        mode = if (m == mode) {
            -1
        } else {
            m
        }
        // Log.d("TEST:V", "mode: $mode")
        return mode
    }

    override fun onDraw(canvas: Canvas) {


        // currentMatrix.postTranslate(xShift.toFloat(), yShift.toFloat())

        canvas.concat(currentMatrix)
        // canvas.concat(m)


        // draw lines over it
        if (PDFbitmap != null) {
            // setImageBitmap(PDFbitmap)
            canvas.drawBitmap(PDFbitmap!!, defaultMatrix, null)
        }

        for (i in 0 until paths.size) {
            canvas.drawPath(paths[i].first, brushes[paths[i].second])
        }

        super.onDraw(canvas)
    }

}