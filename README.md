# RxArcFace
RxArcFace —— 基于虹软人脸识别SDK的Rx封装

[简书](https://www.jianshu.com/p/7b8ff014c3b1)

# 简介

虽然各厂商为我们提供了优质的人脸识别SDK，但其中包含了较多的无意义代码，例如错误处理，检测，剖析，而开发者在接入时往往不是非常关心这些事情，RxArcFace旨在将虹软人脸识别SDK的模板化操作封装，并结合RxJava2，带给开发者流畅的开发体验

## Demo截图

![a.jpg](https://upload-images.jianshu.io/upload_images/17794320-ee18aedbc25488c8.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

> SDK准备工作请参考 
> https://ai.arcsoft.com.cn/manual/docs#/139 
> https://ai.arcsoft.com.cn/manual/docs#/140  **只需看3.1**
> 这里将不再累述

# 使用 RxArcFace
- clone项目到本地 https://github.com/ZYF99/RxArcFace.git
- 在需要使用的项目中 引入RxArcFace的Module
![](https://upload-images.jianshu.io/upload_images/17794320-5d00bce45301557c.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
- 选中刚才克隆下的项目文件夹中的RxArcFaceModule
![](https://upload-images.jianshu.io/upload_images/17794320-f04c0ef7ceda8aa9.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
- 在自己项目的app的build.gradle中添加依赖
```
implementation project(path: ':RxArcFacelibrary')
```
![](https://upload-images.jianshu.io/upload_images/17794320-7ce81e4cbacfddbd.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
- 将自己在官网下载的SDK依赖包替换掉 RxArcFaceLibrary下libs、jniLibs 中的依赖包


#### 添加权限

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA"/>
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.autofocus"/>
```

#### 将需要匹配的数据类实现 `IFaceDetect` 接口

```
data class Person(
    val id: Long? = null,
    val name: String? = null,
    val avatar: String? = null, //添加avatar属性
    var faceCode: String? = null //添加faceCode可变属性
) : IFaceDetect {
    override fun getFaceCodeJson(): String? {
        return faceCode
    }

    override fun getAvatarUrl(): String? {
        return avatar
    }

    override fun bindFaceCode(faceCodeJson: String?) {
        faceCode = faceCodeJson
    }
}
```

#### 也许你会问为什么我还需要自己添加faceCode属性和avatar属性呢？

其实并不是需要你自己去添加，往往我们在接入人脸识别功能时，我们早就有了自己的数据类，这跟数据类很可能是后端返回给我们的，而我们有时候很难决定后端会给我们什么样的数据， `faceCode` 和 `avatar` 只是说我们的数据类必须有这两种东西（一个人脸特征，一个头像），它们可以是你之前就有的，也可以是你后来添加的，假如后端本身就返回给我们一个 属性作为人脸特征，那么我们直接在 `getFaceCodeJson` 返回它就好，`avatar`同理。

### 摄像头采集图像

```
    private var camera: Camera? = null
    
    //初始化相机、surfaceView
    private fun initCameraOrigin(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                //surface创建时执行
                if (camera == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        camera = openCamera(this@MainActivity) { data, camera, resWidth, resHeight ->
                                if (data != null && data.size > 1) {
                                	//TODO 人脸匹配
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
```



#### 使用人脸识别匹配

```
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
```



### 完整的Activity代码

```
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
		
		//初始化人脸识别引擎
        initArcSoftEngine(
            this,
            "输入官网申请的appid",
            "输入官网申请的"
        )

		//初始化摄像头
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
```
## 注意：Demo没有检查相机权限，自行在设置去打开权限或者自己添加权限检测

## 请作者喝杯咖啡吧~
![qq_pic_merged_1608260264392.jpg](https://upload-images.jianshu.io/upload_images/17794320-823a9710246c4272.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

## 个人博客
[ZIKI(安卓学弟)](https://zyf99.github.io/Blog/)
	
## License

	Copyright 2020, ZEKI
	
	   Licensed under the Apache License, Version 2.0 (the "License");
	   you may not use this file except in compliance with the License.
	   You may obtain a copy of the License at
	
	       http://www.apache.org/licenses/LICENSE-2.0
	
	   Unless required by applicable law or agreed to in writing, software
	   distributed under the License is distributed on an "AS IS" BASIS,
	   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	   See the License for the specific language governing permissions and
	   limitations under the License.
