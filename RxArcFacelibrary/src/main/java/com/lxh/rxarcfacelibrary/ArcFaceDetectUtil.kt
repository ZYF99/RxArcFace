package com.lxh.rxarcfacelibrary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.arcsoft.face.*
import com.arcsoft.face.enums.DetectFaceOrientPriority
import com.arcsoft.face.enums.DetectMode
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

//(虹软)判断为同一人的阈值，大于此值即可判断为同一人
const val ARC_SOFT_VALUE_MATCHED = 0.8f

private var context: Context? = null

//虹软人脸初始化分析引擎
private val faceDetectEngine = FaceEngine()

//虹软人脸识别引擎
private val faceEngine = FaceEngine()

//检测人脸时间戳
var lastFaceDetectingTime = 0L

//是否正在检测
var isFaceDetecting = false

/**
 * (虹软)初始化人脸识别引擎
 * */
fun initArcSoftEngine(
    contextTemp: Context,
    arcAppId: String,
    arcSdkKey: String
) {
    context = contextTemp
    val activeCode = FaceEngine.activeOnline(
        context,
        arcAppId,
        arcSdkKey
    )
    Log.d("激活虹软,结果码：", activeCode.toString())
    val faceEngineCode = faceEngine.init(
        context,
        DetectMode.ASF_DETECT_MODE_IMAGE,
        DetectFaceOrientPriority.ASF_OP_270_ONLY,
        16,
        6,
        FaceEngine.ASF_FACE_RECOGNITION or FaceEngine.ASF_AGE or FaceEngine.ASF_FACE_DETECT or FaceEngine.ASF_GENDER or FaceEngine.ASF_FACE3DANGLE
    )

    faceDetectEngine.init(
        context,
        DetectMode.ASF_DETECT_MODE_VIDEO,
        DetectFaceOrientPriority.ASF_OP_ALL_OUT,
        16,
        6,
        FaceEngine.ASF_FACE_RECOGNITION or FaceEngine.ASF_AGE or FaceEngine.ASF_FACE_DETECT or FaceEngine.ASF_GENDER or FaceEngine.ASF_FACE3DANGLE
    )

    Log.d("FaceEngine init", "initEngine: init $faceEngineCode")
    when (faceEngineCode) {
        ErrorInfo.MOK,
        ErrorInfo.MERR_ASF_ALREADY_ACTIVATED -> {
        }
        else -> Log.d("ARCFACEUTIL", "初始化虹软人脸识别错误，Code${faceEngineCode}")
    }
}

/**
 * (虹软)通过人员人脸图片url，获取带特征码人员列表
 * */
@Synchronized
fun <T : IFaceDetect> detectPersonAvatarAndBindFaceFeatureCodeByArcSoft(
    personListTemp: List<T>?
): Single<List<T>> {
    return Observable.fromIterable(personListTemp)
        .flatMapSingle { person ->
            getArcFaceCodeByPicUrl(person.getAvatarUrl())
                .map { arcFaceCodeJson ->
                    person.bindFaceCode(arcFaceCodeJson)
                    person
                }
        }
        .toList()
        .subscribeOn(Schedulers.io())
}

/**
 * (虹软)通过人员人脸图片byteArray，为人员绑定上特征码
 * */
@Synchronized
fun <T : IFaceDetect> bindFaceCodeByByteArray(
    person: T,
    imageByteArray: ByteArray,
    imageWidth: Int,
    imageHeight: Int
): Single<T> {
    return getArcFaceCodeByImageData(
        imageByteArray,
        imageWidth,
        imageHeight
    ).flatMap {
        Single.just(person.apply {
            bindFaceCode(it)
        })
    }.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
}

/**
 * (虹软)通过一个人脸图片识别匹配是否为某个人类
 * */
@Synchronized
fun <T : IFaceDetect> matchHumanFaceSoloByArcSoft(
    data: ByteArray,
    width: Int,
    height: Int,
    previewWidth: Int? = null,
    previewHeight: Int? = null,
    human: T,
    doOnMatched: (T) -> Unit,
    doOnMatchMissing: (() -> Unit)? = null,
    doFinally: (() -> Unit)? = null
) {
    matchHumanFaceListByArcSoft(
        data = data,
        width = width,
        height = height,
        previewWidth = previewWidth,
        previewHeight = previewHeight,
        humanList = listOf(human),
        doOnMatchedHuman = doOnMatched,
        doOnMatchMissing = doOnMatchMissing,
        doFinally = doFinally
    )
}


/**
 * (虹软)通过人脸图片识别匹配列表里的人类
 * */
@Synchronized
fun <T : IFaceDetect> matchHumanFaceListByArcSoft(
    data: ByteArray,
    width: Int,
    height: Int,
    previewWidth: Int? = null,
    previewHeight: Int? = null,
    humanList: List<T>,
    doOnMatchedHuman: (T) -> Unit,
    doOnMatchMissing: (() -> Unit)? = null,
    doFinally: (() -> Unit)? = null
) {
    if (isFaceDetecting
        || System.currentTimeMillis() - lastFaceDetectingTime <= 1000
    ) return

    //正在检测
    isFaceDetecting = true

    //上次检测时间
    lastFaceDetectingTime = System.currentTimeMillis()

    //人脸列表
    val faceInfoList: List<FaceInfo> = mutableListOf()

    //⼈脸检测
    val detectCode = faceEngine.detectFaces(
        data,
        width,
        height,
        FaceEngine.CP_PAF_NV21,
        faceInfoList
    )
    if (detectCode != 0 || faceInfoList.isEmpty()) {
        Log.d(
            "ARCFACE",
            "face detection finished, code is " + detectCode + ", face num is " + faceInfoList.size
        )
        doFinally?.invoke()
        isFaceDetecting = false
        return
    }

    //人脸剖析
    val faceProcessCode = faceEngine.process(
        data,
        width,
        height,
        FaceEngine.CP_PAF_NV21,
        faceInfoList,
        FaceEngine.ASF_AGE or FaceEngine.ASF_GENDER or FaceEngine.ASF_FACE3DANGLE
    )

    //剖析失败
    if (faceProcessCode != ErrorInfo.MOK) {
        Log.d("ARCFACE", "face process finished , code is $faceProcessCode")
        doFinally?.invoke()
        isFaceDetecting = false
        return
    }

    //previewWidth和previewHeight不为空表示需要人脸在画面中间
    val needAvatarInViewCenter =
        previewWidth != null
                && previewHeight != null
                && isAvatarInViewCenter(faceInfoList[0].rect, previewWidth, previewHeight)

    //previewWidth和previewHeight为空表示不需要人脸在画面中间
    val doNotNeedAvatarInViewCenter = previewWidth == null && previewHeight == null

    when {
        (faceInfoList.isNotEmpty() && needAvatarInViewCenter)
                || (faceInfoList.isNotEmpty() && doNotNeedAvatarInViewCenter) -> {
        }
        else -> {//无人脸，退出匹配
            doFinally?.invoke()
            isFaceDetecting = false
            return
        }
    }

    //识别到的人脸特征
    val currentFaceFeature = FaceFeature()

    //人脸特征分析
    val res = faceEngine.extractFaceFeature(
        data,
        width,
        height,
        FaceEngine.CP_PAF_NV21,
        faceInfoList[0],
        currentFaceFeature
    )

    //人脸特征分析失败
    if (res != ErrorInfo.MOK) {
        doFinally?.invoke()
        isFaceDetecting = false
        return
    }

    //进行遍历匹配
    val matchedMeetingPerson = humanList.find {
        val faceSimilar = FaceSimilar()
        val startDetectTime = System.currentTimeMillis()
        if (it.getFaceCodeJson() == null || it.getFaceCodeJson()!!.isEmpty()) {
            return@find false
        }
        val compareResult =
            faceEngine.compareFaceFeature(
                globalMoshi.fromJson(it.getFaceCodeJson()),
                currentFaceFeature,
                faceSimilar
            )
        Log.d("单个参会人匹配耗时", "${System.currentTimeMillis() - startDetectTime}")
        if (compareResult == ErrorInfo.MOK) {
            Log.d("相似度", faceSimilar.score.toString())
            faceSimilar.score > ARC_SOFT_VALUE_MATCHED
        } else {
            Log.d("对比发生错误", compareResult.toString())
            false
        }
    }
    if (matchedMeetingPerson == null) {
        //匹配到的人为空
        doOnMatchMissing?.invoke()
    } else {
        //匹配到的人
        doOnMatchedHuman(matchedMeetingPerson)
    }
}

/**
 * 通过照片加载为ArcFaceCode
 * */
private fun getArcFaceCodeByPicUrl(
    picUrl: String?
): Single<String> {
    return Single.create { emitter ->
        Glide.with(context!!)
            .asBitmap()
            .load(picUrl)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean
                ): Boolean {
                    emitter.onSuccess("")
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .into(object : SimpleTarget<Bitmap>() {
                @Synchronized
                override fun onResourceReady(
                    bitMap: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    val detectStartTime = System.currentTimeMillis()
                    //人脸列表
                    val faceInfoList: List<FaceInfo> = mutableListOf()
                    val faceByteArray = getPixelsBGR(bitMap)
                    //⼈脸检测
                    val detectCode = faceDetectEngine.detectFaces(
                        faceByteArray,
                        bitMap.width,
                        bitMap.height,
                        FaceEngine.CP_PAF_BGR24,
                        faceInfoList
                    )
                    if (detectCode == 0) {
                        //人脸剖析
                        val faceProcessCode = faceDetectEngine.process(
                            faceByteArray,
                            bitMap.width,
                            bitMap.height,
                            FaceEngine.CP_PAF_BGR24,
                            faceInfoList,
                            FaceEngine.ASF_AGE or FaceEngine.ASF_GENDER or FaceEngine.ASF_FACE3DANGLE
                        )
                        //剖析成功
                        if (faceProcessCode == ErrorInfo.MOK && faceInfoList.isNotEmpty()) {
                            //识别到的人脸特征
                            val currentFaceFeature = FaceFeature()
                            //人脸特征分析
                            val res = faceDetectEngine.extractFaceFeature(
                                faceByteArray,
                                bitMap.width,
                                bitMap.height,
                                FaceEngine.CP_PAF_BGR24,
                                faceInfoList[0],
                                currentFaceFeature
                            )
                            //人脸特征分析成功
                            if (res == ErrorInfo.MOK) {
                                Log.d(
                                    "!!人脸转换耗时",
                                    "${System.currentTimeMillis() - detectStartTime}"
                                )
                                Schedulers.io().scheduleDirect {
                                    emitter.onSuccess(globalMoshi.toJson(currentFaceFeature))
                                }
                            }
                        } else {
                            Log.d("ARCFACE", "face process finished , code is $faceProcessCode")
                            Schedulers.io().scheduleDirect {
                                emitter.onSuccess("")
                            }
                        }

                    } else {
                        Log.d(
                            "ARCFACE",
                            "face detection finished, code is " + detectCode + ", face num is " + faceInfoList.size
                        )
                        Schedulers.io().scheduleDirect {
                            emitter.onSuccess("")
                        }
                    }
                }
            })
    }
}

/**
 * 通过图片数据加载为ArcFaceCode
 * */
private fun getArcFaceCodeByImageData(
    imageData: ByteArray,
    imageWidth: Int,
    imageHeight: Int
): Single<String> {
    return Single.create { emitter ->
        val detectStartTime = System.currentTimeMillis()
        //人脸列表
        val faceInfoList: List<FaceInfo> = mutableListOf()

        //⼈脸检测
        val detectCode = faceDetectEngine.detectFaces(
            imageData,
            imageWidth,
            imageHeight,
            FaceEngine.CP_PAF_NV21,
            faceInfoList
        )
        if (detectCode == 0) {
            //人脸剖析
            val faceProcessCode = faceDetectEngine.process(
                imageData,
                imageWidth,
                imageHeight,
                FaceEngine.CP_PAF_NV21,
                faceInfoList,
                FaceEngine.ASF_AGE or FaceEngine.ASF_GENDER or FaceEngine.ASF_FACE3DANGLE
            )
            //剖析成功
            if (faceProcessCode == ErrorInfo.MOK && faceInfoList.isNotEmpty()) {
                //识别到的人脸特征
                val currentFaceFeature = FaceFeature()
                //人脸特征分析
                val res = faceDetectEngine.extractFaceFeature(
                    imageData,
                    imageWidth,
                    imageHeight,
                    FaceEngine.CP_PAF_NV21,
                    faceInfoList[0],
                    currentFaceFeature
                )
                //人脸特征分析成功
                if (res == ErrorInfo.MOK) {
                    Log.d(
                        "!!人脸转换耗时",
                        "${System.currentTimeMillis() - detectStartTime}"
                    )
                    Schedulers.io().scheduleDirect {
                        emitter.onSuccess(globalMoshi.toJson(currentFaceFeature))
                    }
                }
            } else {
                Log.d("ARCFACE", "face process finished , code is $faceProcessCode")
                Schedulers.io().scheduleDirect {
                    emitter.onSuccess("")
                }
            }

        } else {
            Log.d(
                "ARCFACE",
                "face detection finished, code is " + detectCode + ", face num is " + faceInfoList.size
            )
            Schedulers.io().scheduleDirect {
                emitter.onSuccess("")
            }
        }
    }
}

/**
 * 判断人脸是否在View的中间
 * */
fun isAvatarInViewCenter(rect: Rect, previewWidth: Int, previewHeight: Int): Boolean {
    try {
        val minSX = previewHeight / 10f
        val minZY = kotlin.math.abs(previewWidth - previewHeight) / 2 + minSX

        val isLeft = kotlin.math.abs(rect.left) > minZY
        val isTop = kotlin.math.abs(rect.top) > minSX
        val isRight = kotlin.math.abs(rect.left) + rect.width() < (previewWidth - minZY)
        val isBottom = kotlin.math.abs(rect.top) + rect.height() < (previewHeight - minSX)
        if (isLeft && isTop && isRight && isBottom) return true
    } catch (e: Exception) {
        Log.e("ARCFACE", e.localizedMessage)
    }
    return false
}

/**
 * 销毁人脸检测对象
 * */
fun unInitArcFaceEngine() {
    faceDetectEngine
    faceEngine.unInit()
}

