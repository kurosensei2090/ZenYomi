package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.MainAPI

open class TmdbProvider : MainAPI() {
    override var name = "TMDB"
    override var mainUrl = "https://api.themoviedb.org"
}
