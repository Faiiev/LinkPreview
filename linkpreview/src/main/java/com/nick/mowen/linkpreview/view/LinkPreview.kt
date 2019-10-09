package com.nick.mowen.linkpreview.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.databinding.DataBindingUtil
import coil.api.load
import com.nick.mowen.linkpreview.ImageType
import com.nick.mowen.linkpreview.R
import com.nick.mowen.linkpreview.databinding.PreviewBinding
import com.nick.mowen.linkpreview.extension.*
import com.nick.mowen.linkpreview.listener.LinkClickListener
import com.nick.mowen.linkpreview.listener.LinkListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Suppress("MemberVisibilityCanBePrivate") // Leave values as protected for extensibility
open class LinkPreview : FrameLayout, View.OnClickListener {

    protected lateinit var binding: PreviewBinding
    /** Map of cached links and their image url */
    private var linkMap: HashMap<Int, String> = hashMapOf()
    /** Type of image to handle in specific way */
    private var imageType = ImageType.NONE
    /** Parsed URL */
    protected var url = ""
    /** Optional listener for load callbacks */
    var loadListener: LinkListener? = null
    /** Optional click listener to override click behavior */
    var clickListener: LinkClickListener? = null
    /** Set whether or not to default to hidden while loading preview */
    var articleColor: Int = Color.CYAN
    /** Color of the Chrome CustomTab that is launched on view click */
    var hideWhileLoading = false

    constructor(context: Context) : super(context) {
        bindViews(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        bindViews(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        bindViews(context)
    }

    /**
     * Convenience method to add views to layout
     *
     * @param context for inflating view
     */
    private fun bindViews(context: Context) {
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.preview, this, true)
        binding.root.let {
            this.minimumHeight = it.minimumHeight
            this.minimumWidth = it.minimumWidth
        }

        if (isInEditMode)
            return

        if (hideWhileLoading)
            isGone = true


        setOnClickListener(this)
        GlobalScope.launch { linkMap = context.loadLinkMap() }
    }

    /**
     * Handles article clicking based on TYPE of article
     *
     * @param view [LinkPreview] that was clicked
     */
    override fun onClick(view: View?) {
        if (clickListener != null)
            clickListener?.onLinkClicked(view, url)
        else {
            when (imageType) {
                ImageType.DEFAULT -> {
                    val chromeTab = CustomTabsIntent.Builder()
                        .setToolbarColor(articleColor)
                        .addDefaultShareMenuItem()
                        .enableUrlBarHiding()
                        .build()

                    try {
                        chromeTab.launchUrl(context, url.toUri())
                    } catch (e: Exception) {
                        //context.showToast("Could not open article")
                        e.printStackTrace()
                    }
                }
                ImageType.YOUTUBE -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                else -> {
                }
            }
        }
    }

    /**
     * Sets the actual text of the view handling multiple types of images including the link cache
     */
    private fun setText() {
        if (linkMap.containsKey(url.hashCode())) {
            val code = linkMap[url.hashCode()]

            if (code != null && code != "Fail") {
                imageType = ImageType.DEFAULT
                setImageData(code)
            }
        } else {
            if (url.let { it.contains("youtube") && it.contains("v=") }) {
                val id = url.split("v=")[1].split(" ")[0]
                val imageUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg"
                imageType = ImageType.YOUTUBE
                context.addLink(url, imageUrl)
                setImageData(imageUrl)
            } else {
                try {
                    visibility = View.VISIBLE
                    binding.previewText.text = url
                    imageType = ImageType.DEFAULT
                    GlobalScope.launch { loadImage(url, linkMap, url.hashCode(), loadListener) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    imageType = ImageType.NONE
                    visibility = View.GONE
                    loadListener?.onError()
                }
            }
        }
    }

    /**
     * Handles loading the article image using Glide
     *
     * @param link to image url
     */
    fun setImageData(link: String) {
        if (!linkMap.containsKey(url.hashCode())) {
            linkMap[url.hashCode()] = link
            context.addLink(url, link)
        }

        binding.previewImage.load(link) { crossfade(true) }
        binding.previewText.text = url

        if (visibility != View.VISIBLE)
            visibility = View.VISIBLE
    }

    /**
     * Allows easy passing of possible link text to check for links that can be handled by [LinkPreview]
     *
     * @param text entire body to search for link
     * @return if a link was found in the text
     */
    fun parseTextForLink(text: String): Boolean {
        return when {
            text.contains("youtube") && text.contains("v=") -> {
                val id = text.split("v=")[1].split(" ")[0]
                url = "https://www.youtube.com/watch?v=$id"
                setText()
                true
            }
            text.contains("youtu.be") -> {
                val id = text.split("be/")[1].split(" ")[0]
                url = "https://www.youtube.com/watch?v=$id"
                setText()
                true
            }
            text.contains("http") -> {
                url = text.parseUrl()
                setText()
                true
            }
            else -> {
                imageType = ImageType.NONE
                visibility = View.GONE
                false
            }
        }
    }

    /**
     * Allows direct setting of url if already known
     *
     * @param link which contains only the url and nothing else
     */
    fun setLink(link: String) {
        if (link.isUrl()) {
            url = link
            setText()
        } else
            throw IllegalArgumentException("String is not a valid link, if you want to parse full text use LinkPreview.parseTextForLink")
    }
}