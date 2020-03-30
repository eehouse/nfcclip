/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
/*
 * Copyright 2020 by Eric House (eehouse@eehouse.org).  All rights reserved.
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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ClipFragment extends PageFragment
    implements View.OnClickListener,
               ClipboardManager.OnPrimaryClipChangedListener,
               NFCUtils.Callbacks {
    private final String TAG = ClipFragment.class.getSimpleName();
    private View mParentView;
    private ClipData.Item mClipData;
    private String[] mType = {null};
    private String[] mLabel = {null};

    @Override
    public void onResume() {
        super.onResume();
        showHideDisabled();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        mParentView = view;
        view.findViewById(R.id.send).setOnClickListener( this );
        view.findViewById(R.id.enable).setOnClickListener( this );
        ((TextView)view.findViewById(R.id.clip_text))
            .setMovementMethod(new ScrollingMovementMethod());

        view.getViewTreeObserver().
            addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
                    @Override
                    public void onWindowFocusChanged(final boolean hasFocus)
                    {
                        if ( hasFocus ) {
                            getClipData();
                        }
                    }
                } );

        ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                .addPrimaryClipChangedListener( this );
    }

    @Override
    public void onClick(View view)
    {
        int id = view.getId();
        switch ( id ) {
        case R.id.send:
            NFCUtils.sendClip( getActivity(), this, mType[0], mLabel[0], mClipData );
            break;
        case R.id.enable:
            Intent intent = new Intent( Settings.ACTION_NFC_SETTINGS )
                .addFlags( Intent.FLAG_ACTIVITY_NEW_DOCUMENT );
            getActivity().startActivity( intent );
            break;
        default:
            assert false;
        }
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
        if ( succeeded ) {
            getClipData();
        }
    }

    @Override
    public void onProgressMade( final int cur, final int max )
    {
        getActivity().runOnUiThread( new Runnable() {
                @Override
                public void run() {
                    ProgressBar pb = (ProgressBar)mParentView.findViewById( R.id.progress );
                    pb.setMax(max);
                    pb.setProgress(cur);
                }
            } );
    }

    private void showSending( final boolean sending )
    {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mParentView.findViewById(R.id.send)
                    .setVisibility( sending ? View.GONE : View.VISIBLE );
                mParentView.findViewById(R.id.sending_status)
                    .setVisibility( !sending ? View.GONE : View.VISIBLE );
            }
        });
    }

    private void getClipData()
    {
        getActivity().runOnUiThread( new Runnable() {
                @Override
                public void run() {
                    Context context = getContext();
                    mClipData = Clip.getData( context, mType, mLabel );
                    Log.d( TAG, "got clip data: " + mClipData );

                    TextView tv = (TextView)mParentView.findViewById(R.id.clip_label);
                    if ( null == mLabel[0] ) {
                        tv.setVisibility( View.GONE );
                    } else {
                        tv.setVisibility( View.VISIBLE );
                        tv.setText( getString( R.string.labelLabel, mLabel[0] ) );
                    }
                    tv = (TextView)mParentView.findViewById(R.id.clip_type);
                    tv.setText( getString( R.string.typeLabel, mType[0] ) );

                    if (null != mClipData) {
                        tv = (TextView) mParentView.findViewById(R.id.clip_text);
                        tv.setText( mClipData.coerceToText(context) );
                    }

                    mParentView.findViewById(R.id.send).setEnabled( mClipData != null );
                }
            } );
    }

    private void showHideDisabled()
    {
        boolean enabled = NFCUtils.nfcEnabled( getContext() );
        mParentView.findViewById( R.id.disabled_expl )
            .setVisibility( enabled ? View.GONE : View.VISIBLE );
        mParentView.findViewById( R.id.send )
            .setVisibility( !enabled ? View.GONE : View.VISIBLE );
    }
}
