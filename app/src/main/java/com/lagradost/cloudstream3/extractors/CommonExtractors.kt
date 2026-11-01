package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi

open class Filesim : ExtractorApi() {
    override var name = "Filesim"
    override var mainUrl = "files.im"
}

open class GMPlayer : ExtractorApi() {
    override var name = "GMPlayer"
    override var mainUrl = "gmplayer"
}

open class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "jeniusplay"
}

open class PixelDrain : ExtractorApi() {
    override var name = "PixelDrain"
    override var mainUrl = "pixeldrain.com"
}

open class StreamSB : ExtractorApi() {
    override var name = "StreamSB"
    override var mainUrl = "streamsb"
}

open class Voe : ExtractorApi() {
    override var name = "Voe"
    override var mainUrl = "voe.sx"
}
