package io.github.gmathi.novellibrary.activity

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import com.bumptech.glide.Glide
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.util.Utils
import kotlinx.android.synthetic.main.activity_image_preview.*
import java.io.File


class ImagePreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            val decorView = window.decorView
            val option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            decorView.systemUiVisibility = option
            getWindow().statusBarColor = ContextCompat.getColor(this, R.color.colorBackground)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val url = intent.getStringExtra("url")
        val filePath = intent.getStringExtra("filePath")



        if (Utils.checkNetwork(this) && url != null)
            Glide.with(this).load(url).into(previewImageView)
        else if (filePath != null)
            Glide.with(this).load(File(filePath.replace("file://", ""))).into(previewImageView)
    }

}
