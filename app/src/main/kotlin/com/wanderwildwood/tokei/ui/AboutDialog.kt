package com.wanderwildwood.tokei.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.tokei.BuildConfig
import com.wanderwildwood.tokei.R

/**
 * What this is, what it does with your data, the terms it comes under, and where its source is.
 * A page of its own, paged on whole rows, so none of it is cut off at the foot of the screen.
 * The base it was made from, Fossify Clock, is named, as its licence asks.
 */
@Composable
fun AboutPage(onBack: () -> Unit) {
    var licences by rememberSaveable { mutableStateOf(false) }
    if (licences) {
        LicencesPage(onBack = { licences = false })
        return
    }
    SubPage(stringResource(R.string.about_page_title), onBack) {
        item(key = "title") {
            TextMMD(
                text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            TextMMD(text = stringResource(R.string.about_what), style = helperStyle())
        }
        item(key = "ring") { Paragraph(R.string.about_ring_title, R.string.about_ring) }
        item(key = "privacy") { Paragraph(R.string.about_privacy_title, R.string.about_privacy) }
        item(key = "privacy2") { Paragraph(null, R.string.about_privacy_links) }
        item(key = "terms") { Paragraph(R.string.about_terms_title, R.string.about_terms) }
        item(key = "terms2") { Paragraph(null, R.string.about_terms_alarms) }
        item(key = "credits") {
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringResource(R.string.about_base), style = helperStyle())
        }
        item(key = "licences") {
            TextMMD(
                text = stringResource(R.string.about_licences),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { licences = true }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            )
        }
        item(key = "source") {
            TextMMD(text = "github.com/wanderwildwood/tokei", style = helperStyle(), modifier = Modifier.openLink("https://github.com/wanderwildwood/tokei"))
        }
        item(key = "llama") {
            Spacer(Modifier.height(6.dp))
            Llama()
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A short paragraph, with a bold heading over it when it has one. */
@Composable
private fun Paragraph(title: Int?, body: Int) {
    Column(Modifier.fillMaxWidth().padding(top = if (title != null) 14.dp else 6.dp)) {
        if (title != null) {
            TextMMD(text = stringResource(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        TextMMD(text = stringResource(body), style = helperStyle())
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to. The site's
 * address sits at the start of the same line and opens the site; the llama and its words
 * open the page.
 *
 * Straight to the checkout: the Donate button on the site only leads there anyway. The short
 * square.link form, which is what the site itself links to, so a regenerated checkout follows
 * it. A phone with nothing that opens a web address says so rather than doing nothing.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(
            text = "wanderthe.dev",
            style = helperStyle(),
            // The site's address opens the site, the way the llama beside it opens its page.
            modifier = Modifier
                .openLink("https://wanderthe.dev"),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .openLink("https://square.link/u/AGu8oT10"),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = helperStyle())
        }
    }
}

/** A target of at least 48 dp that opens [url] in a real browser, or says there is none. */
@Composable
private fun Modifier.openLink(url: String): Modifier {
    val context = LocalContext.current
    val shown = url.removePrefix("https://")
    return this
        .clickable { openLink(context, url, context.getString(R.string.about_no_browser_address, shown)) }
        .heightIn(min = 48.dp)
        .wrapContentHeight(Alignment.CenterVertically)
}
