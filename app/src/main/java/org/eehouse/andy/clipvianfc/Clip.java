/* -*- compile-command: "find-and-gradle.sh inXw4dDeb"; -*- */
/*
 * Copyright 2020 by Eric House (xwords@eehouse.org).  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.eehouse.andy.clipvianfc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class Clip {
    private final static String TAG = Clip.class.getSimpleName();

    static String getData( Context context )
    {
        String pasteData = null;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if ( clipboard.hasPrimaryClip()
             && clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN) ) {

             ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
             pasteData = item.getText().toString();

             if ( null == pasteData ) {
                 Uri pasteUri = item.getUri();
                 if ( null != pasteUri ) {
                     pasteData = resolve( pasteUri );
                 }
             }
        }
        return pasteData;
    }

    static void setData( Context context, String data )
    {
        ClipboardManager clipboard = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("simple text", data );
        clipboard.setPrimaryClip( clip );
    }

    private static String resolve( Uri uri )
    {
        Log.e( TAG, "resolve(" + uri + ") not doing anything" );
        return null;
    }
}
