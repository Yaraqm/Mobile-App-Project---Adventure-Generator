package com.example.mobileproject.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.mobileproject.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

class ImagePreviewDialogFragment : DialogFragment() {

    private var mediaUrl: String? = null
    private var mediaType: String? = null
    private var onDeleteClicked: (() -> Unit)? = null

    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaUrl = arguments?.getString("mediaUrl")
        mediaType = arguments?.getString("mediaType")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.TransparentDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_preview)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val imageView = dialog.findViewById<ImageView>(R.id.dialogImage)
        val closeBtn = dialog.findViewById<ImageButton>(R.id.btnClose)
        val deleteBtn = dialog.findViewById<ImageButton>(R.id.btnDelete)
        playerView = dialog.findViewById(R.id.dialogVideoPlayer)

        if (mediaType == "video") {
            // Show video player
            imageView.visibility = View.GONE
            playerView?.visibility = View.VISIBLE

            val url = mediaUrl
            if (url != null) {
                exoPlayer = ExoPlayer.Builder(requireContext()).build().also { player ->
                    playerView?.player = player
                    val mediaItem = MediaItem.fromUri(url)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                }
            }
        } else {
            // Show image
            playerView?.visibility = View.GONE
            imageView.visibility = View.VISIBLE

            Glide.with(requireContext())
                .load(mediaUrl)
                .into(imageView)
        }

        closeBtn.setOnClickListener {
            dismiss()
        }
        deleteBtn.setOnClickListener {
            onDeleteClicked?.invoke()
            dismiss()
        }

        return dialog
    }

    fun setOnDeleteClicked(listener: () -> Unit) {
        onDeleteClicked = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        playerView = null
    }

    companion object {
        fun newInstance(mediaUrl: String, mediaType: String): ImagePreviewDialogFragment {
            val fragment = ImagePreviewDialogFragment()
            val args = Bundle()
            args.putString("mediaUrl", mediaUrl)
            args.putString("mediaType", mediaType)
            fragment.arguments = args
            return fragment
        }
    }
}
