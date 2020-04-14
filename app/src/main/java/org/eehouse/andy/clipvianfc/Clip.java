/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
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
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class Clip {
    private final static String TAG = Clip.class.getSimpleName();

    static ClipData.Item getData( Context context, String[] mimeTypeOut, String[] labelOut )
    {
        ClipData.Item pasteData = null;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if ( clipboard.hasPrimaryClip() ) {
            ClipDescription desc = clipboard.getPrimaryClipDescription();
            int count = desc.getMimeTypeCount();
            for ( int ii = 0; ii < count; ++ii ) {
                String typ = desc.getMimeType( ii );
                Log.d( TAG, "got type " + ii + ": " + typ);
                if ( typ.startsWith("text/")) {
                    pasteData = clipboard.getPrimaryClip().getItemAt(0);
                    mimeTypeOut[0] = typ;
                    CharSequence label = desc.getLabel();
                    labelOut[0] = label == null ? null : label.toString();
                    break;
                }
            }
        }
        Log.d( TAG, "getData() => " + pasteData);
        return pasteData;
    }

    static void setData( Context context, String mimeType, String label, String data )
    {
        ClipboardManager clipboard = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText( label, data );
        clipboard.setPrimaryClip( clip );
    }

    private static String resolve( Uri uri )
    {
        Log.e( TAG, "resolve(" + uri + ") not doing anything" );
        return null;
    }
}
