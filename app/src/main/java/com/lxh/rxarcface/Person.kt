package com.lxh.rxarcface

import com.lxh.rxarcfacelibrary.IFaceDetect

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