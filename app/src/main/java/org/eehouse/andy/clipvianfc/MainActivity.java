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
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/* TODO
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
                                                               ClipboardManager.OnPrimaryClipChangedListener,
                                                               NFCUtils.Callbacks {
    private final static String TAG = MainActivity.class.getSimpleName();

    private ClipData.Item mClipData;
    private String[] mType = {null};
    private String[] mLabel = {null};
    private boolean mHaveNFC;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mHaveNFC = NFCUtils.deviceSupportsNFC( this );
        if ( !mHaveNFC ) {
            setContentView( R.layout.activity_no_nfc );
            findViewById(R.id.uninstall).setOnClickListener( this );
        } else {
            setContentView(R.layout.activity_main);
            ((TextView)findViewById(R.id.clip_text)).setMovementMethod(new ScrollingMovementMethod());
            findViewById(R.id.send).setOnClickListener( this );
            findViewById(R.id.enable).setOnClickListener( this );

            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                .addPrimaryClipChangedListener( this );
        }
    }

    @Override
    protected void onPostResume()
    {
        super.onPostResume();
        if ( mHaveNFC ) {
            showHideDisabled();
        }
    }

    // On Android 10 you can't get at the clipboard unless you are the
    // in-focus app. So that's where we'll check for contents.
    @Override
    public void onWindowFocusChanged( boolean hasFocus )
    {
        super.onWindowFocusChanged(hasFocus);
        if ( hasFocus ) {
            getClipData();
        }
    }

    @Override
    public void onClick(View view)
    {
        int id = view.getId();
        switch ( id ) {
        case R.id.send:
            NFCUtils.sendClip( this, this, mType[0], mLabel[0], mClipData );
            break;
        case R.id.uninstall:
            uninstall();
            break;
        case R.id.enable:
            Intent intent = new Intent( Settings.ACTION_NFC_SETTINGS )
                .addFlags( Intent.FLAG_ACTIVITY_NEW_DOCUMENT );
            startActivity( intent );
            break;
        default:
            assert false;
        }
    }

    private void getClipData()
    {
        mClipData = Clip.getData( this, mType, mLabel );

        TextView tv = (TextView)findViewById(R.id.clip_label);
        if ( null == mLabel[0] ) {
            tv.setVisibility( View.GONE );
        } else {
            tv.setVisibility( View.VISIBLE );
            tv.setText( getString( R.string.labelLabel, mLabel[0] ) );
        }
        tv = (TextView)findViewById(R.id.clip_type);
        tv.setText( getString( R.string.typeLabel, mType[0] ) );

        if (null != mClipData) {
            tv = (TextView) findViewById(R.id.clip_text);
            tv.setText(mClipData.coerceToHtmlText(this));
        }

        findViewById(R.id.send).setEnabled( mClipData != null );
    }

    private void uninstall()
    {
        Intent intent = new Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID ) )
            .putExtra( "android.intent.extra.UNINSTALL_ALL_USERS", true);
        startActivity( intent );
        finish();
    }

    private void showHideDisabled()
    {
        boolean enabled = NFCUtils.nfcEnabled( this );
        findViewById(R.id.disabled_expl).setVisibility( enabled ? View.GONE : View.VISIBLE );
        findViewById(R.id.send).setVisibility( !enabled ? View.GONE : View.VISIBLE );
    }

    @Override
    public void onPrimaryClipChanged()
    {
        getClipData();
    }

    @Override
    public void onSendEnabled()
    {
        showSending( true );
    }

    @Override
    public void onSendComplete( boolean succeeded )
    {
        showSending( false );
    }

    @Override
    public void onProgressMade( final int cur, final int max )
    {
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ProgressBar pb = (ProgressBar)findViewById( R.id.progress );
                    pb.setMax(max);
                    pb.setProgress(cur);
                }
            } );
    }

    private void showSending( final boolean sending )
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.send).setVisibility( sending ? View.GONE : View.VISIBLE );
                findViewById(R.id.sending_status).setVisibility( !sending ? View.GONE : View.VISIBLE );
            }
        });
    }

    static Intent getSelfIntent( Context context )
    {
        Intent intent = new Intent( context, MainActivity.class )
            .setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP )
            ;
        return intent;
    }
}
