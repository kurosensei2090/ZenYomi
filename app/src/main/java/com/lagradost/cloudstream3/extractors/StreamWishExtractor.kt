package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi

open class StreamWishExtractor : ExtractorApi() {
    override var name = "StreamWish"
    override var mainUrl = "https://streamwish.to"
    override val requiresReferer = false
}

open class VidStack : ExtractorApi() {
    override var name = "VidStack"
    override var mainUrl = "https://vidstack.io"
    override val requiresReferer = false
}
