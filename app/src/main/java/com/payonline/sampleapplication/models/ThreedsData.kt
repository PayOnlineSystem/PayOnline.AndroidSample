package com.payonline.sampleapplication.models

import sdk.FiscalPayload
import sdk.FiscalStatus
import java.io.Serializable

open class ThreedsData :Serializable {

    constructor(acsUrl:String, pareq: String, md: String, termUrl: String){
        this.acsUrl = acsUrl
        this.md = md
        this.pareq = pareq
        this.termUrl = termUrl
    }

    val acsUrl: String
    val pareq: String
    val md: String
    val termUrl: String
}