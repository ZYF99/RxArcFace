package com.lxh.rxarcface

import android.hardware.Camera
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import com.lxh.rxarcfacelibrary.bindFaceCodeByByteArray
import com.lxh.rxarcfacelibrary.initArcSoftEngine
import com.lxh.rxarcfacelibrary.isFaceDetecting
import com.lxh.rxarcfacelibrary.matchHumanFaceListByArcSoft

class MainActivity : AppCompatActivity() {

    private var camera: Camera? = null
    private var listOfPerson: MutableList<Person> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initArcSoftEngine(
            this,
            "",
            ""
        )

        initCameraOrigin(findViewById(R.id.surface_view))
    }

    //初始化相机、surfaceView
    private fun initCameraOrigin(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                //surface创建时执行
                if (camera == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        camera =
                            openCamera(this@MainActivity) { data, camera, resWidth, resHeight ->
                                if (data != null && data.size > 1) {
                                    matchHumanFaceListByArcSoft(
                                        data = data,
                                        width = resWidth,
                                        height = resHeight,
                                        humanList = listOfPerson,
                                        doOnMatchedHuman = { matchedPerson ->
                                            Toast.makeText(
                                                this@MainActivity,
                                                "匹配到${matchedPerson.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isFaceDetecting = false
                                        },
                                        doOnMatchMissing = {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "没匹配到人，正在录入",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            //为一个新的人绑定人脸数据
                                            bindFaceCodeByByteArray(
                                                Person(name = "帅哥"),
                                                data,
                                                resWidth,
                                                resHeight
                                            ).doOnSuccess {
                                                //往当前列表加入新注册的人
                                                listOfPerson.add(it)
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "录入成功",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isFaceDetecting = false
                                            }.subscribe()

                                        },
                                        doFinally = { }
                                    )
                                }
                            }
                    }
                }
                //调整摄像头方向
                camera?.let { setCameraDisplayOrientation(this@MainActivity, it) }

                //开始预览
                holder.let { camera?.startPreview(it) }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {

            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                camera.releaseCamera()
                camera = null
            }
        })
    }

    override fun onPause() {
        camera?.setPreviewCallback(null)
        camera.releaseCamera()//释放相机资源
        camera = null
        super.onPause()
    }

    override fun onDestroy() {
        camera?.setPreviewCallback(null)
        camera.releaseCamera()//释放相机资源
        camera = null
        super.onDestroy()
    }

}

