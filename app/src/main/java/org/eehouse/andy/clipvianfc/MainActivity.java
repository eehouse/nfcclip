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
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/* TODO
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener, NFCUtils.Callbacks {
    private final static String TAG = MainActivity.class.getSimpleName();

    private ClipData.Item mClipData;
    private String[] mType = {null};
    private String[] mLabel = {null};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.send).setOnClickListener( this );

        ((TextView)findViewById(R.id.clip_text)).setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onPostResume()
    {
        super.onPostResume();
        mClipData = Clip.getData( this, mType, mLabel );

        TextView tv = (TextView)findViewById(R.id.clip_label);
        tv.setText( getString( R.string.labelLabel, mLabel[0] ) );
        tv = (TextView)findViewById(R.id.clip_type);
        tv.setText( getString( R.string.typeLabel, mType[0] ) );

        tv = (TextView)findViewById(R.id.clip_text);
        tv.setText( mClipData.coerceToHtmlText(this) );

        findViewById(R.id.send).setEnabled( mClipData != null );
    }

    @Override
    public void onClick(View view)
    {
        int id = view.getId();
        switch ( id ) {
            case R.id.send:
                NFCUtils.sendClip( this, this, mType[0], mLabel[0], mClipData );
                break;
            default:
                assert false;
        }
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
}
