package com.example.mobileproject.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.mobileproject.R

class ImagePreviewDialogFragment : DialogFragment() {

    private var imageUrl: String? = null
    private var onDeleteClicked: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageUrl = arguments?.getString("imageUrl")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.TransparentDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_preview)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val imageView = dialog.findViewById<ImageView>(R.id.dialogImage)
        val closeBtn = dialog.findViewById<ImageButton>(R.id.btnClose)
        val deleteBtn = dialog.findViewById<ImageButton>(R.id.btnDelete)

        Glide.with(requireContext())
            .load(imageUrl)
            .into(imageView)

        closeBtn.setOnClickListener { dismiss() }
        deleteBtn.setOnClickListener {
            onDeleteClicked?.invoke()
            dismiss()
        }

        return dialog
    }

    fun setOnDeleteClicked(listener: () -> Unit) {
        onDeleteClicked = listener
    }

    companion object {
        fun newInstance(imageUrl: String): ImagePreviewDialogFragment {
            val fragment = ImagePreviewDialogFragment()
            val args = Bundle()
            args.putString("imageUrl", imageUrl)
            fragment.arguments = args
            return fragment
        }
    }
}
