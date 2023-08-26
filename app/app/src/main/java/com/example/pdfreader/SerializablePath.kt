package com.example.pdfreader

import android.graphics.Path
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import kotlin.jvm.Throws


class SerializablePath : Path(), Serializable {

    interface PAction {
        fun getXVal() : Float
        fun getYVal() : Float
    }

    class LineAct(var x : Float, var y : Float) : PAction, Serializable {

        companion object {
            @JvmStatic
            private val sUID : Long = 97482042857281951L
        }
        override fun getXVal(): Float {
            return x
        }

        override fun getYVal(): Float {
            return y
        }

    }

    class MoveAct(var x : Float, var y : Float) : PAction, Serializable {

        companion object {
            @JvmStatic
            private val sUID : Long = 97482042857281950L
        }
        override fun getXVal(): Float {
            return x
        }

        override fun getYVal(): Float {
            return y
        }

    }

    companion object {
        @JvmStatic
        private val sUID : Long = 97482042857281952L
    }

    private val acts = ArrayList<SerializablePath.PAction>()

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(istream : ObjectInputStream) {
        istream.defaultReadObject()
        drawPath()
    }

    override fun moveTo(x: Float, y: Float) {
        acts.add(MoveAct(x,y))
        super.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        acts.add(LineAct(x,y))
        super.lineTo(x, y)
    }

    private fun drawPath() {
        for (a in acts) {
            if (a is SerializablePath.MoveAct) {
                super.moveTo(a.getXVal(), a.getYVal())
            }
            else if (a is SerializablePath.LineAct) {
                super.lineTo(a.getXVal(), a.getYVal())
            }
        }
    }

}