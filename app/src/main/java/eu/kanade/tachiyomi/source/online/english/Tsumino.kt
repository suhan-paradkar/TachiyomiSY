package eu.kanade.tachiyomi.source.online.english

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.TsuminoSearchMetadata
import exh.metadata.metadata.TsuminoSearchMetadata.Companion.TAG_TYPE_DEFAULT
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.util.dropBlank
import exh.util.trimAll
import exh.util.urlImportFetchSearchManga
import java.text.SimpleDateFormat
import java.util.Locale
import org.jsoup.nodes.Document
import rx.Observable

class Tsumino(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    LewdSource<TsuminoSearchMetadata, Document>,
    UrlImportableSource {
    override val metaClass = TsuminoSearchMetadata::class
    override val lang = "en"

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase(Locale.ROOT) ?: return null
        if (lcFirstPathSegment != "read" && lcFirstPathSegment != "book" && lcFirstPathSegment != "entry") {
            return null
        }
        return "https://tsumino.com/Book/Info/${uri.lastPathSegment}"
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .flatMap {
                parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga))
            }
    }

    override fun parseIntoMetadata(metadata: TsuminoSearchMetadata, input: Document) {
        with(metadata) {
            tmId = TsuminoSearchMetadata.tmIdFromUrl(input.location())!!.toInt()
            tags.clear()

            input.getElementById("Title")?.text()?.let {
                title = it.trim()
            }

            input.getElementById("Artist")?.children()?.first()?.text()?.trim()?.let { artistString ->
                artistString.split("|").trimAll().dropBlank().forEach {
                    tags.add(RaisedTag("artist", it, TAG_TYPE_DEFAULT))
                }
                tags.add(RaisedTag("artist", artistString, TAG_TYPE_VIRTUAL))
                artist = artistString
            }

            input.getElementById("Uploader")?.children()?.first()?.text()?.trim()?.let {
                uploader = it
            }

            input.getElementById("Uploaded")?.text()?.let {
                uploadDate = TM_DATE_FORMAT.parse(it.trim())!!.time
            }

            input.getElementById("Pages")?.text()?.let {
                length = it.trim().toIntOrNull()
            }

            input.getElementById("Rating")?.text()?.let {
                ratingString = it.trim()
            }

            input.getElementById("Category")?.children()?.first()?.text()?.let {
                category = it.trim()
                tags.add(RaisedTag("genre", it, TAG_TYPE_VIRTUAL))
            }

            input.getElementById("Collection")?.children()?.first()?.text()?.let {
                collection = it.trim()
                tags.add(RaisedTag("collection", it, TAG_TYPE_DEFAULT))
            }

            input.getElementById("Group")?.children()?.first()?.text()?.let {
                group = it.trim()
                tags.add(RaisedTag("group", it, TAG_TYPE_DEFAULT))
            }

            val newParody = mutableListOf<String>()
            input.getElementById("Parody")?.children()?.forEach {
                val entry = it.text().trim()
                newParody.add(entry)
                tags.add(RaisedTag("parody", entry, TAG_TYPE_DEFAULT))
            }
            parody = newParody

            val newCharacter = mutableListOf<String>()
            input.getElementById("Character")?.children()?.forEach {
                val entry = it.text().trim()
                newCharacter.add(entry)
                tags.add(RaisedTag("character", entry, TAG_TYPE_DEFAULT))
            }
            character = newCharacter

            input.getElementById("Tag")?.children()?.let { tagElements ->
                tags.addAll(
                    tagElements.map {
                        RaisedTag("tags", it.text().trim(), TAG_TYPE_DEFAULT)
                    }
                )
            }
        }
    }

    override val matchingHosts = listOf(
        "www.tsumino.com",
        "tsumino.com"
    )

    companion object {
        val TM_DATE_FORMAT = SimpleDateFormat("yyyy MMM dd", Locale.US)
    }
}
