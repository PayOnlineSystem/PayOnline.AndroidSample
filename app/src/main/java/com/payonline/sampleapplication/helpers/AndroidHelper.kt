package com.payonline.sampleapplication.helpers

import android.content.Context
import androidx.appcompat.app.AlertDialog

public final class AndroidHelper{

    public fun makeMessageDialog(context: Context, title: String, message: String){
        if(message.isNullOrEmpty() || context == null)
            return

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
            .show()
    }

}